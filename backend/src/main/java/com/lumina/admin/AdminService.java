package com.lumina.admin;

import com.lumina.api.Requests.RejectRequest;
import com.lumina.api.Requests.ResolveReportRequest;
import com.lumina.api.Views.AdminDashboard;
import com.lumina.api.Views.AuditView;
import com.lumina.api.Views.CourseCard;
import com.lumina.api.Views.ReportView;
import com.lumina.api.Views.UserView;
import com.lumina.audit.AuditService;
import com.lumina.common.Errors;
import com.lumina.common.Pages;
import com.lumina.common.Pages.PageData;
import com.lumina.common.TxRunner;
import com.lumina.course.CourseCardFactory;
import com.lumina.course.CourseFinder;
import com.lumina.domain.AuditLog;
import com.lumina.domain.Course;
import com.lumina.domain.Enums.AccountStatus;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.LessonStatus;
import com.lumina.domain.Enums.ReportStatus;
import com.lumina.domain.Enums.Role;
import com.lumina.domain.Enums.VideoStatus;
import com.lumina.domain.Report;
import com.lumina.domain.User;
import com.lumina.notify.NotificationService;
import com.lumina.repo.AuditLogRepository;
import com.lumina.repo.CourseRepository;
import com.lumina.repo.EnrollmentRepository;
import com.lumina.repo.ReportRepository;
import com.lumina.repo.UserRepository;
import com.lumina.security.AuthUser;
import com.lumina.user.UserMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AdminService {
    private final UserRepository users;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final ReportRepository reports;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final NotificationService notifications;
    private final CourseCardFactory cards;
    private final TxRunner tx;
    private final MongoTemplate mongo;

    public AdminService(UserRepository users, CourseRepository courses, EnrollmentRepository enrollments, ReportRepository reports,
                        AuditLogRepository auditLogs, AuditService audit, NotificationService notifications, CourseCardFactory cards,
                        TxRunner tx, MongoTemplate mongo) {
        this.users = users;
        this.courses = courses;
        this.enrollments = enrollments;
        this.reports = reports;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.notifications = notifications;
        this.cards = cards;
        this.tx = tx;
        this.mongo = mongo;
    }

    public AdminDashboard dashboard() {
        Map<String, Long> byStatus = new HashMap<>();
        for (org.bson.Document row : mongo.aggregate(Aggregation.newAggregation(
                Aggregation.group("status").count().as("count"),
                Aggregation.project("count").and("_id").as("status")
        ), "courses", org.bson.Document.class).getMappedResults()) {
            byStatus.put(String.valueOf(row.get("status")), number(row.get("count")));
        }
        long lessons = 0;
        long ready = 0;
        for (Course course : courses.findAll()) {
            lessons += course.activeLessonCount();
            ready += course.getSections().stream().flatMap(section -> section.getLessons().stream())
                    .filter(lesson -> lesson.getVideo() != null && lesson.getVideo().getStatus() == VideoStatus.READY).count();
        }
        List<UserView> recentUsers = mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(6), User.class)
                .stream().map(UserMapper::toView).toList();
        List<AuditView> recentAudit = mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(8), AuditLog.class)
                .stream().map(this::auditView).toList();
        return new AdminDashboard(
                users.countByRole(Role.STUDENT),
                users.countByRole(Role.INSTRUCTOR),
                users.countByRole(Role.ADMIN),
                byStatus,
                lessons,
                ready,
                enrollments.count(),
                reports.countByStatus(ReportStatus.OPEN),
                recentUsers,
                recentAudit
        );
    }

    public PageData<UserView> users(String q, Role role, AccountStatus status, Integer page, Integer size) {
        int pageNumber = Pages.page(page);
        int pageSize = Pages.size(size, 12);
        List<Criteria> parts = new java.util.ArrayList<>();
        if (role != null) parts.add(Criteria.where("role").is(role));
        if (status != null) parts.add(Criteria.where("status").is(status));
        if (q != null && !q.isBlank()) {
            String safe = Pattern.quote(q.trim());
            parts.add(new Criteria().orOperator(
                    Criteria.where("fullName").regex(safe, "i"),
                    Criteria.where("email").regex(safe, "i")
            ));
        }
        Query query = parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts));
        long total = mongo.count(query, User.class);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).skip((long) pageNumber * pageSize).limit(pageSize);
        return PageData.of(mongo.find(query, User.class).stream().map(UserMapper::toView).toList(), pageNumber, pageSize, total);
    }

    public UserView user(String id) {
        return UserMapper.toView(users.findById(id).orElseThrow(() -> Errors.notFound("User not found.")));
    }

    public UserView setStatus(AuthUser actor, String id, AccountStatus status) {
        User user = users.findById(id).orElseThrow(() -> Errors.notFound("User not found."));
        if (user.getId().equals(actor.id()) && status == AccountStatus.SUSPENDED) {
            throw Errors.bad("INVALID_STATE", "You cannot suspend your own account.");
        }
        user.setStatus(status);
        users.save(user);
        audit.log(actor.id(), actor.role(), "USER_STATUS", "USER", id, status.name());
        return UserMapper.toView(user);
    }

    public UserView setRole(AuthUser actor, String id, Role role) {
        User user = users.findById(id).orElseThrow(() -> Errors.notFound("User not found."));
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw Errors.bad("INVALID_STATE", "The last admin cannot be demoted.");
        }
        user.setRole(role);
        users.save(user);
        audit.log(actor.id(), actor.role(), "USER_ROLE", "USER", id, role.name());
        return UserMapper.toView(user);
    }

    public PageData<CourseCard> courses(CourseStatus status, String q, Integer page, Integer size) {
        int pageNumber = Pages.page(page);
        int pageSize = Pages.size(size, 12);
        List<Criteria> parts = new java.util.ArrayList<>();
        if (status != null) parts.add(Criteria.where("status").is(status));
        if (q != null && !q.isBlank()) parts.add(Criteria.where("title").regex(Pattern.quote(q.trim()), "i"));
        Query query = parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts));
        long total = mongo.count(query, Course.class);
        query.with(Sort.by(Sort.Direction.DESC, "updatedAt")).skip((long) pageNumber * pageSize).limit(pageSize);
        return PageData.of(mongo.find(query, Course.class).stream().map(course -> cards.card(course, null)).toList(), pageNumber, pageSize, total);
    }

    public CourseCard approve(AuthUser actor, String courseId) {
        Course[] saved = new Course[1];
        tx.run(() -> {
            Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
            if (course.getStatus() != CourseStatus.PENDING_REVIEW) {
                throw Errors.conflict("INVALID_STATE", "Only a course waiting for review can be approved.");
            }
            course.setStatus(CourseStatus.PUBLISHED);
            course.setRejectionReason(null);
            course.setReviewedAt(Instant.now());
            course.setReviewedBy(actor.id());
            course.setPublishedAt(course.getPublishedAt() == null ? course.getReviewedAt() : course.getPublishedAt());
            courses.save(course);
            audit.log(actor.id(), actor.role(), "COURSE_APPROVED", "COURSE", courseId, course.getTitle());
            saved[0] = course;
        });
        notifications.notify(saved[0].getInstructorId(), "COURSE_APPROVED", "Course approved",
                saved[0].getTitle() + " is now published.", "/teach/courses/" + courseId);
        return cards.card(saved[0], null);
    }

    public CourseCard reject(AuthUser actor, String courseId, RejectRequest request) {
        Course[] saved = new Course[1];
        tx.run(() -> {
            Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
            if (course.getStatus() != CourseStatus.PENDING_REVIEW) {
                throw Errors.conflict("INVALID_STATE", "Only a course waiting for review can be rejected.");
            }
            course.setStatus(CourseStatus.REJECTED);
            course.setRejectionReason(request.reason().trim());
            course.setReviewedAt(Instant.now());
            course.setReviewedBy(actor.id());
            courses.save(course);
            audit.log(actor.id(), actor.role(), "COURSE_REJECTED", "COURSE", courseId, request.reason().trim());
            saved[0] = course;
        });
        notifications.notify(saved[0].getInstructorId(), "COURSE_REJECTED", "Course needs changes",
                saved[0].getTitle() + ": " + request.reason().trim(), "/teach/" + courseId);
        return cards.card(saved[0], null);
    }

    public CourseCard archive(AuthUser actor, String courseId) {
        Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
        course.setStatus(CourseStatus.ARCHIVED);
        course.setUpdatedAt(Instant.now());
        courses.save(course);
        audit.log(actor.id(), actor.role(), "COURSE_ARCHIVED", "COURSE", courseId, "Soft-archived. Existing enrollments remain.");
        return cards.card(course, null);
    }

    public CourseCard disable(AuthUser actor, String courseId, boolean disabled, String reason) {
        Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
        course.setContentDisabled(disabled);
        course.setDisabledReason(disabled ? reason : null);
        course.setUpdatedAt(Instant.now());
        courses.save(course);
        audit.log(actor.id(), actor.role(), disabled ? "COURSE_DISABLED" : "COURSE_ENABLED", "COURSE", courseId, reason);
        return cards.card(course, null);
    }

    public void removeLesson(AuthUser actor, String courseId, String lessonId, String reason) {
        Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
        Course.Lesson lesson = CourseFinder.requireLesson(course, lessonId).lesson();
        lesson.setStatus(LessonStatus.REMOVED);
        course.recomputeDuration();
        course.setUpdatedAt(Instant.now());
        courses.save(course);
        audit.log(actor.id(), actor.role(), "LESSON_REMOVED", "LESSON", lessonId, reason == null ? course.getTitle() : reason);
    }

    public PageData<ReportView> reports(ReportStatus status, Integer page, Integer size) {
        int pageNumber = Pages.page(page);
        int pageSize = Pages.size(size, 12);
        Query query = status == null ? new Query() : Query.query(Criteria.where("status").is(status));
        long total = mongo.count(query, Report.class);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).skip((long) pageNumber * pageSize).limit(pageSize);
        return PageData.of(mongo.find(query, Report.class).stream().map(this::reportView).toList(), pageNumber, pageSize, total);
    }

    public ReportView resolve(AuthUser actor, String id, ResolveReportRequest request) {
        Report report = reports.findById(id).orElseThrow(() -> Errors.notFound("Report not found."));
        if (request.status() != ReportStatus.RESOLVED && request.status() != ReportStatus.DISMISSED && request.status() != ReportStatus.UNDER_REVIEW) {
            throw Errors.bad("INVALID_STATE", "Choose under review, resolved, or dismissed.");
        }
        report.setStatus(request.status());
        report.setResolution(request.resolution());
        report.setResolvedBy(actor.id());
        report.setResolvedAt(Instant.now());
        reports.save(report);
        audit.log(actor.id(), actor.role(), "REPORT_" + request.status().name(), "REPORT", id, request.resolution());
        return reportView(report);
    }

    public PageData<AuditView> audit(Integer page, Integer size) {
        int pageNumber = Pages.page(page);
        int pageSize = Pages.size(size, 20);
        Query query = new Query();
        long total = mongo.count(query, AuditLog.class);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).skip((long) pageNumber * pageSize).limit(pageSize);
        return PageData.of(mongo.find(query, AuditLog.class).stream().map(this::auditView).toList(), pageNumber, pageSize, total);
    }

    public Report createReport(AuthUser actor, com.lumina.api.Requests.ReportRequest request) {
        Report report = new Report();
        report.setReporterId(actor.id());
        report.setReporterName(actor.fullName());
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setCourseId(request.courseId());
        report.setReason(request.reason().trim());
        report.setDescription(request.description());
        report.setStatus(ReportStatus.OPEN);
        report.setCreatedAt(Instant.now());
        return reports.save(report);
    }

    private ReportView reportView(Report report) {
        return new ReportView(report.getId(), report.getReporterName(), report.getTargetType().name(), report.getTargetId(),
                report.getCourseId(), report.getReason(), report.getDescription(), report.getStatus().name(), report.getResolution(),
                report.getCreatedAt(), report.isSeedData(),
                report.getAiAssessment() == null ? null : report.getAiAssessment().getRiskCategory(),
                report.getAiAssessment() == null ? null : report.getAiAssessment().getSeverity(),
                report.getAiAssessment() == null ? null : report.getAiAssessment().getExplanation());
    }

    private AuditView auditView(AuditLog log) {
        return new AuditView(log.getId(), log.getActorId(), log.getActorRole() == null ? null : log.getActorRole().name(),
                log.getAction(), log.getTargetType(), log.getTargetId(), log.getDetails(), log.getCreatedAt(), log.isSeedData());
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}

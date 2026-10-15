package com.lumina.learning;

import com.lumina.api.Requests.CompleteRequest;
import com.lumina.api.Requests.NoteRequest;
import com.lumina.api.Requests.ProgressRequest;
import com.lumina.api.Requests.ReviewRequest;
import com.lumina.api.Views.EnrollmentView;
import com.lumina.api.Views.HistoryItem;
import com.lumina.api.Views.NoteView;
import com.lumina.api.Views.ProgressView;
import com.lumina.api.Views.ReviewView;
import com.lumina.api.Views.StudentDashboard;
import com.lumina.common.Errors;
import com.lumina.common.Pages;
import com.lumina.common.Pages.PageData;
import com.lumina.course.CourseCardFactory;
import com.lumina.course.CourseFinder;
import com.lumina.domain.Certificate;
import com.lumina.domain.Course;
import com.lumina.domain.Enrollment;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.EnrollmentStatus;
import com.lumina.domain.Enums.EventType;
import com.lumina.domain.Enums.LessonStatus;
import com.lumina.domain.Enums.ProgressStatus;
import com.lumina.domain.Enums.VideoStatus;
import com.lumina.domain.LearningEvent;
import com.lumina.domain.LessonProgress;
import com.lumina.domain.Note;
import com.lumina.domain.Review;
import com.lumina.domain.User;
import com.lumina.notify.NotificationService;
import com.lumina.repo.CertificateRepository;
import com.lumina.repo.CourseRepository;
import com.lumina.repo.EnrollmentRepository;
import com.lumina.repo.LearningEventRepository;
import com.lumina.repo.LessonProgressRepository;
import com.lumina.repo.NoteRepository;
import com.lumina.repo.ReviewRepository;
import com.lumina.repo.UserRepository;
import com.lumina.security.AuthUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class LearningService {
    private final CourseRepository courses;
    private final CertificateRepository certificates;
    private final EnrollmentRepository enrollments;
    private final LessonProgressRepository progressRepo;
    private final LearningEventRepository events;
    private final NoteRepository notes;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final NotificationService notifications;
    private final CourseCardFactory cards;
    private final MongoTemplate mongo;
    private final Clock clock;
    private final WatchProgressPolicy policy = new WatchProgressPolicy();

    public LearningService(CourseRepository courses, CertificateRepository certificates, EnrollmentRepository enrollments, LessonProgressRepository progressRepo,
                           LearningEventRepository events, NoteRepository notes, ReviewRepository reviews, UserRepository users,
                           NotificationService notifications, CourseCardFactory cards, MongoTemplate mongo, Clock clock) {
        this.courses = courses;
        this.certificates = certificates;
        this.enrollments = enrollments;
        this.progressRepo = progressRepo;
        this.events = events;
        this.notes = notes;
        this.reviews = reviews;
        this.users = users;
        this.notifications = notifications;
        this.cards = cards;
        this.mongo = mongo;
        this.clock = clock;
    }

    public EnrollmentView enroll(AuthUser actor, String courseId) {
        if (actor.role() != com.lumina.domain.Enums.Role.STUDENT) {
            throw Errors.forbidden("Only students can enroll.");
        }
        Course course = courses.findById(courseId).orElseThrow(() -> Errors.notFound("Course not found."));
        if (course.getStatus() != CourseStatus.PUBLISHED || course.isContentDisabled()) {
            throw Errors.conflict("COURSE_NOT_PUBLISHED", "Only a published course can be enrolled.");
        }
        if (enrollments.findByStudentIdAndCourseId(actor.id(), courseId).isPresent()) {
            throw Errors.conflict("ALREADY_ENROLLED", "You are already enrolled in this course.");
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(actor.id());
        enrollment.setCourseId(courseId);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setEnrolledAt(clock.instant());
        enrollment.setLastAccessedAt(enrollment.getEnrolledAt());
        enrollment.setCurriculumVersionAtEnrollment(course.getCurriculumVersion());
        enrollment.setCountableLessonCount(course.countableLessonCount());
        try {
            enrollments.save(enrollment);
        } catch (DuplicateKeyException ex) {
            throw Errors.conflict("ALREADY_ENROLLED", "You are already enrolled in this course.");
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(courseId)), new Update().inc("stats.enrollmentCount", 1), Course.class);
        event(actor.id(), courseId, null, EventType.ENROLLED, null);
        notifications.notify(course.getInstructorId(), "ENROLLMENT", "New enrollment",
                actor.fullName() + " enrolled in " + course.getTitle(), "/teach/" + courseId);
        return toEnrollment(enrollment, course);
    }

    public List<EnrollmentView> myEnrollments(AuthUser actor) {
        return enrollments.findByStudentIdOrderByLastAccessedAtDesc(actor.id()).stream()
                .map(item -> toEnrollment(item, courses.findById(item.getCourseId()).orElse(null)))
                .toList();
    }

    public StudentDashboard dashboard(AuthUser actor) {
        List<Enrollment> mine = enrollments.findByStudentIdOrderByLastAccessedAtDesc(actor.id());
        int completed = (int) mine.stream().filter(item -> item.getStatus() == EnrollmentStatus.COMPLETED).count();
        int inProgress = (int) mine.stream().filter(item -> item.getProgressPercent() > 0 && item.getStatus() != EnrollmentStatus.COMPLETED).count();
        double average = mine.isEmpty() ? 0 : mine.stream().mapToDouble(Enrollment::getProgressPercent).average().orElse(0);
        List<EnrollmentView> cont = mine.stream().limit(4)
                .map(item -> toEnrollment(item, courses.findById(item.getCourseId()).orElse(null)))
                .toList();
        List<HistoryItem> recent = history(actor, 0, 5).items();
        return new StudentDashboard(mine.size(), completed, inProgress, Math.round(average * 10.0) / 10.0, cont, recent);
    }

    public ProgressView updateProgress(AuthUser actor, ProgressRequest request) {
        Enrollment enrollment = requireEnrollment(actor.id(), request.courseId());
        Course course = courses.findById(request.courseId()).orElseThrow(() -> Errors.notFound("Course not found."));
        CourseFinder.Located located = CourseFinder.requireLesson(course, request.lessonId());
        if (located.lesson().getStatus() != LessonStatus.ACTIVE || located.section().getStatus() != com.lumina.domain.Enums.SectionStatus.ACTIVE) {
            throw Errors.bad("INVALID_STATE", "This lesson is no longer part of the course.");
        }
        if (located.lesson().getVideo() == null || located.lesson().getVideo().getStatus() != VideoStatus.READY) {
            throw new com.lumina.common.ApiException(org.springframework.http.HttpStatus.CONFLICT, "VIDEO_NOT_READY",
                    "This lesson does not have a video yet, so progress cannot be saved.");
        }
        LessonProgress progress = progressRepo.findByStudentIdAndLessonId(actor.id(), request.lessonId()).orElseGet(() -> {
            LessonProgress created = new LessonProgress();
            created.setStudentId(actor.id());
            created.setCourseId(course.getId());
            created.setLessonId(request.lessonId());
            created.setSectionId(located.section().getSectionId());
            created.setFirstOpenedAt(clock.instant());
            created.setStatus(ProgressStatus.IN_PROGRESS);
            return created;
        });
        boolean firstOpen = !progress.isOpenedEventWritten();
        WatchProgressPolicy.Result result = policy.apply(new WatchProgressPolicy.State(
                progress.getLastPositionSeconds(),
                progress.getDurationSeconds(),
                progress.getAccumulatedWatchSeconds(),
                progress.getLastWatchedAt(),
                progress.getStatus() == ProgressStatus.COMPLETED
        ), new WatchProgressPolicy.Update(request.positionSeconds(), request.durationSeconds(), clock.instant()));
        progress.setLastPositionSeconds(result.lastPositionSeconds());
        progress.setDurationSeconds(result.durationSeconds());
        progress.setAccumulatedWatchSeconds(result.accumulatedWatchSeconds());
        progress.setMaxWatchedPercent(Math.max(progress.getMaxWatchedPercent(), result.maxWatchedPercent()));
        progress.setLastWatchedAt(clock.instant());
        if (progress.getFirstOpenedAt() == null) progress.setFirstOpenedAt(clock.instant());
        if (result.justCompleted()) {
            markComplete(progress, "WATCH_THRESHOLD");
        }
        progress.setOpenedEventWritten(true);
        progressRepo.save(progress);
        if (firstOpen) event(actor.id(), course.getId(), request.lessonId(), EventType.LESSON_OPENED, null);
        if (result.milestone() > progress.getHighestMilestone()) {
            progress.setHighestMilestone(result.milestone());
            progressRepo.save(progress);
            LearningEvent milestone = event(actor.id(), course.getId(), request.lessonId(), EventType.PLAYBACK_MILESTONE, null);
            milestone.getPayload().put("milestone", result.milestone());
            events.save(milestone);
        }
        if (result.justCompleted()) {
            event(actor.id(), course.getId(), request.lessonId(), EventType.LESSON_COMPLETED, null);
            recount(enrollment, course);
        } else {
            enrollment.setLastAccessedAt(clock.instant());
            enrollment.setLastLessonId(request.lessonId());
            enrollment.setLastPositionSeconds(result.lastPositionSeconds());
            enrollments.save(enrollment);
        }
        return toProgress(progress, result.justCompleted() ? "Lesson completed." : "Progress saved.");
    }

    public List<ProgressView> progressForCourse(AuthUser actor, String courseId) {
        return progressRepo.findByStudentIdAndCourseId(actor.id(), courseId).stream()
                .map(item -> new ProgressView(item.getLessonId(), item.getCourseId(), item.getStatus().name(),
                        item.getLastPositionSeconds(), item.getDurationSeconds(), item.getAccumulatedWatchSeconds(),
                        item.getMaxWatchedPercent(), item.getStatus() == ProgressStatus.COMPLETED, "Saved progress."))
                .toList();
    }

    public ProgressView complete(AuthUser actor, CompleteRequest request) {
        Enrollment enrollment = requireEnrollment(actor.id(), request.courseId());
        Course course = courses.findById(request.courseId()).orElseThrow(() -> Errors.notFound("Course not found."));
        CourseFinder.requireLesson(course, request.lessonId());
        LessonProgress progress = progressRepo.findByStudentIdAndLessonId(actor.id(), request.lessonId())
                .orElseThrow(() -> Errors.bad("WATCH_THRESHOLD", "Watch more of the lesson before marking it complete."));
        if (progress.getStatus() == ProgressStatus.COMPLETED) {
            return toProgress(progress, "This lesson is already complete.");
        }
        if (!policy.canManuallyComplete(progress.getAccumulatedWatchSeconds(), progress.getDurationSeconds())) {
            throw Errors.bad("WATCH_THRESHOLD", "Watch at least 80 percent of the lesson before marking it complete. Opening the lesson is not enough.");
        }
        markComplete(progress, "MANUAL_AFTER_THRESHOLD");
        progressRepo.save(progress);
        event(actor.id(), course.getId(), request.lessonId(), EventType.LESSON_COMPLETED, null);
        recount(enrollment, course);
        return toProgress(progress, "Lesson marked complete.");
    }

    public PageData<HistoryItem> history(AuthUser actor, Integer page, Integer size) {
        int pageNumber = Pages.page(page);
        int pageSize = Pages.size(size, 20);
        Query query = Query.query(Criteria.where("studentId").is(actor.id()));
        long total = mongo.count(query, LearningEvent.class);
        query.with(Sort.by(Sort.Direction.DESC, "occurredAt")).skip((long) pageNumber * pageSize).limit(pageSize);
        List<HistoryItem> items = mongo.find(query, LearningEvent.class).stream().map(this::toHistory).toList();
        return PageData.of(items, pageNumber, pageSize, total);
    }

    public NoteView addNote(AuthUser actor, NoteRequest request) {
        requireEnrollment(actor.id(), request.courseId());
        Course course = courses.findById(request.courseId()).orElseThrow(() -> Errors.notFound("Course not found."));
        CourseFinder.requireLesson(course, request.lessonId());
        Note note = new Note();
        note.setStudentId(actor.id());
        note.setCourseId(request.courseId());
        note.setLessonId(request.lessonId());
        note.setType(request.type());
        note.setBody(request.body().trim());
        note.setPositionSeconds(request.positionSeconds());
        note.setCreatedAt(clock.instant());
        note.setUpdatedAt(note.getCreatedAt());
        notes.save(note);
        event(actor.id(), request.courseId(), request.lessonId(), EventType.NOTE_CREATED, null);
        return toNote(note, course);
    }

    public List<NoteView> notes(AuthUser actor, String courseId) {
        return notes.findByStudentIdAndCourseIdOrderByCreatedAtDesc(actor.id(), courseId).stream()
                .map(note -> toNote(note, courses.findById(note.getCourseId()).orElse(null)))
                .toList();
    }

    public void deleteNote(AuthUser actor, String id) {
        Note note = notes.findById(id).orElseThrow(() -> Errors.notFound("Note not found."));
        if (!note.getStudentId().equals(actor.id())) throw Errors.forbidden("You can delete only your own notes.");
        notes.delete(note);
    }

    public ReviewView review(AuthUser actor, String courseId, ReviewRequest request) {
        requireEnrollment(actor.id(), courseId);
        Review review = reviews.findByStudentIdAndCourseId(actor.id(), courseId).orElseGet(Review::new);
        review.setStudentId(actor.id());
        review.setStudentName(actor.fullName());
        review.setCourseId(courseId);
        review.setRating(request.rating());
        review.setComment(request.comment() == null ? null : request.comment().trim());
        if (review.getCreatedAt() == null) review.setCreatedAt(clock.instant());
        review.setUpdatedAt(clock.instant());
        reviews.save(review);
        refreshRating(courseId);
        return new ReviewView(review.getId(), review.getStudentName(), review.getRating(), review.getComment(), review.getCreatedAt(), review.isSeedData());
    }

    public List<ReviewView> reviews(String courseId) {
        return reviews.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(review -> new ReviewView(review.getId(), review.getStudentName(), review.getRating(), review.getComment(), review.getCreatedAt(), review.isSeedData()))
                .toList();
    }

    private void recount(Enrollment enrollment, Course course) {
        long completed = progressRepo.countByStudentIdAndCourseIdAndStatus(enrollment.getStudentId(), course.getId(), ProgressStatus.COMPLETED);
        int countable = course.countableLessonCount();
        enrollment.setCompletedLessonCount((int) completed);
        enrollment.setCountableLessonCount(countable);
        double percent = countable == 0 ? 0 : Math.round(completed * 1000.0 / countable) / 10.0;
        enrollment.setProgressPercent(percent);
        enrollment.setLastAccessedAt(clock.instant());
        if (countable > 0 && completed >= countable && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(clock.instant());
            event(enrollment.getStudentId(), course.getId(), null, EventType.COURSE_COMPLETED, null);
            issueCertificate(enrollment.getStudentId(), course.getId(), course.getTitle(), enrollment.getCompletedAt());
            User student = users.findById(enrollment.getStudentId()).orElse(null);
            String studentName = student == null ? "A student" : student.getFullName();
            notifications.notify(course.getInstructorId(), "COURSE_COMPLETED", "Course completed",
                    studentName + " completed " + course.getTitle(), "/teach/" + course.getId());
        }
        enrollments.save(enrollment);
    }

    private void markComplete(LessonProgress progress, String method) {
        progress.setStatus(ProgressStatus.COMPLETED);
        progress.setCompletedAt(clock.instant());
        progress.setCompletionMethod(method);
        progress.setMaxWatchedPercent(Math.max(progress.getMaxWatchedPercent(), 100));
    }

    private void issueCertificate(String studentId, String courseId, String courseTitle, Instant completedAt) {
        if (certificates.findByStudentIdAndCourseId(studentId, courseId).isPresent()) return;
        User student = users.findById(studentId).orElse(null);
        String studentName = student == null ? "Student" : student.getFullName();
        Certificate certificate = new Certificate();
        certificate.setStudentId(studentId);
        certificate.setCourseId(courseId);
        certificate.setStudentName(studentName);
        certificate.setCourseTitle(courseTitle);
        certificate.setCompletedAt(completedAt);
        certificate.setIssuedAt(clock.instant());
        try {
            certificates.save(certificate);
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // one certificate per student and course
        }
    }

    public List<com.lumina.api.Views.CertificateView> myCertificates(AuthUser actor) {
        return certificates.findByStudentIdOrderByIssuedAtDesc(actor.id()).stream()
                .map(this::toCertificate).toList();
    }

    public com.lumina.api.Views.CertificateView certificate(AuthUser actor, String id) {
        Certificate certificate = certificates.findById(id).orElseThrow(() -> Errors.notFound("Certificate not found."));
        if (!certificate.getStudentId().equals(actor.id())) throw Errors.forbidden("You can view only your own certificates.");
        return toCertificate(certificate);
    }

    private com.lumina.api.Views.CertificateView toCertificate(Certificate certificate) {
        return new com.lumina.api.Views.CertificateView(
                certificate.getId(), certificate.getId(), certificate.getStudentId(), certificate.getStudentName(),
                certificate.getCourseId(), certificate.getCourseTitle(), certificate.getCompletedAt(), certificate.getCompletedAt(),
                certificate.getIssuedAt(), certificate.isSeedData());
    }

    private Enrollment requireEnrollment(String studentId, String courseId) {
        return enrollments.findByStudentIdAndCourseId(studentId, courseId)
                .orElseThrow(() -> Errors.forbidden("Enroll in this course before saving progress."));
    }

    private LearningEvent event(String studentId, String courseId, String lessonId, EventType type, String ignored) {
        LearningEvent event = new LearningEvent();
        event.setStudentId(studentId);
        event.setCourseId(courseId);
        event.setLessonId(lessonId);
        event.setEventType(type);
        event.setOccurredAt(clock.instant());
        events.save(event);
        return event;
    }

    private void refreshRating(String courseId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("courseId").is(courseId)),
                Aggregation.group().avg("rating").as("avg").count().as("count")
        );
        AggregationResults<org.bson.Document> results = mongo.aggregate(aggregation, "reviews", org.bson.Document.class);
        org.bson.Document doc = results.getUniqueMappedResult();
        if (doc == null) return;
        double avg = doc.get("avg") instanceof Number number ? number.doubleValue() : 0;
        int count = doc.get("count") instanceof Number number ? number.intValue() : 0;
        mongo.updateFirst(Query.query(Criteria.where("_id").is(courseId)),
                new Update().set("stats.averageRating", Math.round(avg * 10.0) / 10.0).set("stats.reviewCount", count),
                Course.class);
    }

    private EnrollmentView toEnrollment(Enrollment enrollment, Course course) {
        return new EnrollmentView(
                enrollment.getId(),
                enrollment.getCourseId(),
                course == null ? null : course.getSlug(),
                course == null ? "Removed course" : course.getTitle(),
                course == null ? null : CourseCardFactory.thumbnail(course.getThumbnailKey()),
                enrollment.getStatus().name(),
                enrollment.getProgressPercent(),
                enrollment.getCompletedLessonCount(),
                enrollment.getCountableLessonCount(),
                enrollment.getEnrolledAt(),
                enrollment.getLastAccessedAt(),
                enrollment.getLastLessonId(),
                course == null ? null : CourseFinder.lessonTitle(course, enrollment.getLastLessonId()),
                enrollment.getLastPositionSeconds()
        );
    }

    private ProgressView toProgress(LessonProgress progress, String message) {
        return new ProgressView(progress.getLessonId(), progress.getCourseId(), progress.getStatus().name(),
                progress.getLastPositionSeconds(), progress.getDurationSeconds(), progress.getAccumulatedWatchSeconds(),
                progress.getMaxWatchedPercent(), progress.getStatus() == ProgressStatus.COMPLETED, message);
    }

    private HistoryItem toHistory(LearningEvent event) {
        Course course = event.getCourseId() == null ? null : courses.findById(event.getCourseId()).orElse(null);
        return new HistoryItem(event.getId(), event.getEventType().name(), event.getCourseId(),
                course == null ? "Course" : course.getTitle(), course == null ? null : course.getSlug(),
                event.getLessonId(), course == null ? null : CourseFinder.lessonTitle(course, event.getLessonId()),
                event.getOccurredAt());
    }

    private NoteView toNote(Note note, Course course) {
        return new NoteView(note.getId(), note.getCourseId(), note.getLessonId(),
                course == null ? "Lesson" : CourseFinder.lessonTitle(course, note.getLessonId()),
                note.getType().name(), note.getBody(), note.getPositionSeconds(), note.getCreatedAt());
    }
}

package com.lumina.course;

import com.lumina.api.Requests.CourseWriteRequest;
import com.lumina.api.Requests.LessonRequest;
import com.lumina.api.Requests.SectionRequest;
import com.lumina.api.Views.CourseCard;
import com.lumina.api.Views.CourseDetail;
import com.lumina.api.Views.InstructorDashboard;
import com.lumina.api.Views.LearnerRow;
import com.lumina.common.Errors;
import com.lumina.common.Slugger;
import com.lumina.domain.Category;
import com.lumina.domain.Course;
import com.lumina.domain.Enrollment;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.LessonAccess;
import com.lumina.domain.Enums.LessonStatus;
import com.lumina.domain.Enums.SectionStatus;
import com.lumina.domain.Enums.VideoStatus;
import com.lumina.domain.User;
import com.lumina.media.MediaStorage;
import com.lumina.repo.CategoryRepository;
import com.lumina.repo.CourseRepository;
import com.lumina.repo.EnrollmentRepository;
import com.lumina.repo.UserRepository;
import com.lumina.security.AuthUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CourseService {
    private final CourseRepository courses;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;
    private final CourseCardFactory cards;
    private final MediaStorage storage;
    private final MongoTemplate mongo;

    public CourseService(CourseRepository courses, CategoryRepository categories, UserRepository users,
                         EnrollmentRepository enrollments, CourseCardFactory cards, MediaStorage storage, MongoTemplate mongo) {
        this.courses = courses;
        this.categories = categories;
        this.users = users;
        this.enrollments = enrollments;
        this.cards = cards;
        this.storage = storage;
        this.mongo = mongo;
    }

    public Course require(String id) {
        return courses.findById(id).orElseThrow(() -> Errors.notFound("Course not found."));
    }

    public CourseDetail create(AuthUser actor, CourseWriteRequest request) {
        User instructor = users.findById(actor.id()).orElseThrow(() -> Errors.notFound("User not found."));
        Course course = new Course();
        course.setInstructorId(instructor.getId());
        course.setInstructorName(instructor.getFullName());
        course.setStatus(CourseStatus.DRAFT);
        course.setCreatedAt(Instant.now());
        applyWrite(course, request);
        course.setSlug(uniqueSlug(Slugger.slug(request.title()), null));
        course.setUpdatedAt(Instant.now());
        courses.save(course);
        return cards.detail(course, instructor, null, true);
    }

    public CourseDetail update(AuthUser actor, String id, CourseWriteRequest request) {
        Course course = owned(actor, id);
        applyWrite(course, request);
        if (!course.getSlug().startsWith(Slugger.slug(request.title()))) {
            course.setSlug(uniqueSlug(Slugger.slug(request.title()), course.getId()));
        }
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public List<CourseCard> mine(AuthUser actor, CourseStatus status) {
        return courses.findByInstructorId(actor.id()).stream()
                .filter(course -> status == null || course.getStatus() == status)
                .sorted(Comparator.comparing(Course::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(course -> cards.card(course, null))
                .toList();
    }

    public CourseDetail mineOne(AuthUser actor, String id) {
        return detailForOwner(owned(actor, id));
    }

    public CourseDetail addSection(AuthUser actor, String courseId, SectionRequest request) {
        Course course = owned(actor, courseId);
        Course.Section section = new Course.Section();
        section.setSectionId(UUID.randomUUID().toString());
        section.setTitle(request.title().trim());
        section.setDescription(blank(request.description()));
        section.setOrder(nextOrder(course.getSections().stream().filter(item -> item.getStatus() == SectionStatus.ACTIVE).map(Course.Section::getOrder).toList()));
        course.getSections().add(section);
        course.setCurriculumVersion(course.getCurriculumVersion() + 1);
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public CourseDetail updateSection(AuthUser actor, String courseId, String sectionId, SectionRequest request) {
        Course course = owned(actor, courseId);
        Course.Section section = CourseFinder.requireSection(course, sectionId);
        section.setTitle(request.title().trim());
        section.setDescription(blank(request.description()));
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public CourseDetail deleteSection(AuthUser actor, String courseId, String sectionId) {
        Course course = owned(actor, courseId);
        Course.Section section = CourseFinder.requireSection(course, sectionId);
        section.setStatus(SectionStatus.REMOVED);
        section.getLessons().forEach(lesson -> lesson.setStatus(LessonStatus.REMOVED));
        course.setCurriculumVersion(course.getCurriculumVersion() + 1);
        course.recomputeDuration();
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public CourseDetail reorderSections(AuthUser actor, String courseId, List<String> orderedIds) {
        Course course = owned(actor, courseId);
        List<Course.Section> active = course.getSections().stream().filter(section -> section.getStatus() == SectionStatus.ACTIVE).toList();
        if (active.size() != orderedIds.size() || active.stream().anyMatch(section -> !orderedIds.contains(section.getSectionId()))) {
            throw Errors.bad("VALIDATION_ERROR", "The section order does not match the current curriculum.");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            CourseFinder.requireSection(course, orderedIds.get(i)).setOrder(i + 1);
        }
        course.getSections().sort(Comparator.comparingInt(Course.Section::getOrder));
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public CourseDetail addLesson(AuthUser actor, String courseId, String sectionId, LessonRequest request) {
        Course course = owned(actor, courseId);
        Course.Section section = CourseFinder.requireSection(course, sectionId);
        if (section.getStatus() != SectionStatus.ACTIVE) {
            throw Errors.bad("INVALID_STATE", "That section has been removed.");
        }
        if (section.getLessons().stream().filter(lesson -> lesson.getStatus() == LessonStatus.ACTIVE).count() >= 20) {
            throw Errors.bad("LIMIT", "A section can have at most 20 lessons.");
        }
        Course.Lesson lesson = new Course.Lesson();
        lesson.setLessonId(UUID.randomUUID().toString());
        applyLesson(lesson, request);
        lesson.setOrder(nextOrder(section.getLessons().stream().filter(item -> item.getStatus() == LessonStatus.ACTIVE).map(Course.Lesson::getOrder).toList()));
        section.getLessons().add(lesson);
        course.setCurriculumVersion(course.getCurriculumVersion() + 1);
        course.recomputeDuration();
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public CourseDetail updateLesson(AuthUser actor, String courseId, String lessonId, LessonRequest request) {
        Course course = owned(actor, courseId);
        Course.Lesson lesson = CourseFinder.requireLesson(course, lessonId).lesson();
        applyLesson(lesson, request);
        course.recomputeDuration();
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public CourseDetail deleteLesson(AuthUser actor, String courseId, String lessonId) {
        Course course = owned(actor, courseId);
        Course.Lesson lesson = CourseFinder.requireLesson(course, lessonId).lesson();
        lesson.setStatus(LessonStatus.REMOVED);
        course.setCurriculumVersion(course.getCurriculumVersion() + 1);
        course.recomputeDuration();
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public CourseDetail reorderLessons(AuthUser actor, String courseId, String sectionId, List<String> orderedIds) {
        Course course = owned(actor, courseId);
        Course.Section section = CourseFinder.requireSection(course, sectionId);
        List<Course.Lesson> active = section.getLessons().stream().filter(lesson -> lesson.getStatus() == LessonStatus.ACTIVE).toList();
        if (active.size() != orderedIds.size() || active.stream().anyMatch(lesson -> !orderedIds.contains(lesson.getLessonId()))) {
            throw Errors.bad("VALIDATION_ERROR", "The lesson order does not match this section.");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            String id = orderedIds.get(i);
            section.getLessons().stream().filter(lesson -> lesson.getLessonId().equals(id)).findFirst().orElseThrow().setOrder(i + 1);
        }
        section.getLessons().sort(Comparator.comparingInt(Course.Lesson::getOrder));
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public CourseDetail submit(AuthUser actor, String courseId) {
        Course course = owned(actor, courseId);
        if (course.getStatus() != CourseStatus.DRAFT && course.getStatus() != CourseStatus.REJECTED) {
            throw Errors.conflict("INVALID_STATE", "Only a draft or rejected course can be submitted.");
        }
        List<String> problems = new ArrayList<>();
        if (course.getTitle() == null || course.getTitle().isBlank()) problems.add("Add a title.");
        if (course.getShortDescription() == null || course.getShortDescription().isBlank()) problems.add("Add a short description.");
        if (course.getDescription() == null || course.getDescription().isBlank()) problems.add("Add a detailed description.");
        if (course.getCategoryId() == null) problems.add("Choose a category.");
        if (course.getLearningObjectives() == null || course.getLearningObjectives().isEmpty()) problems.add("Add at least one learning objective.");
        boolean hasLesson = course.getSections().stream().anyMatch(section -> section.getStatus() == SectionStatus.ACTIVE
                && section.getLessons().stream().anyMatch(lesson -> lesson.getStatus() == LessonStatus.ACTIVE));
        if (!hasLesson) problems.add("Add at least one section with a lesson.");
        if (!problems.isEmpty()) {
            throw new com.lumina.common.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "NOT_READY", "The course is not ready for review.", problems);
        }
        course.setStatus(CourseStatus.PENDING_REVIEW);
        course.setSubmittedAt(Instant.now());
        course.setRejectionReason(null);
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public CourseDetail withdraw(AuthUser actor, String courseId) {
        Course course = owned(actor, courseId);
        if (course.getStatus() != CourseStatus.PENDING_REVIEW) {
            throw Errors.conflict("INVALID_STATE", "Only a course waiting for review can be withdrawn.");
        }
        course.setStatus(CourseStatus.DRAFT);
        touch(course);
        courses.save(course);
        return detailForOwner(course);
    }

    public void deleteDraft(AuthUser actor, String courseId) {
        Course course = owned(actor, courseId);
        if (enrollments.countByCourseId(courseId) > 0) {
            throw Errors.conflict("HAS_ENROLLMENTS", "This course has enrollments and cannot be deleted.");
        }
        course.getSections().forEach(section -> section.getLessons().forEach(lesson -> {
            if (lesson.getVideo() != null) storage.delete(lesson.getVideo().getStorageKey());
        }));
        storage.delete(course.getThumbnailKey());
        courses.delete(course);
    }

    public InstructorDashboard dashboard(AuthUser actor) {
        List<Course> mine = courses.findByInstructorId(actor.id());
        int published = count(mine, CourseStatus.PUBLISHED);
        int draft = count(mine, CourseStatus.DRAFT);
        int pending = count(mine, CourseStatus.PENDING_REVIEW);
        int rejected = count(mine, CourseStatus.REJECTED);
        int archived = count(mine, CourseStatus.ARCHIVED);
        int lessons = mine.stream().mapToInt(Course::activeLessonCount).sum();
        int ready = mine.stream().mapToInt(course -> (int) course.getSections().stream()
                .flatMap(section -> section.getLessons().stream())
                .filter(lesson -> lesson.getVideo() != null && lesson.getVideo().getStatus() == VideoStatus.READY)
                .count()).sum();
        List<String> ids = mine.stream().map(Course::getId).toList();
        List<Enrollment> joined = ids.isEmpty() ? List.of() : mongo.find(Query.query(Criteria.where("courseId").in(ids)), Enrollment.class);
        long completed = joined.stream().filter(item -> item.getStatus() == com.lumina.domain.Enums.EnrollmentStatus.COMPLETED).count();
        double average = joined.isEmpty() ? 0 : joined.stream().mapToDouble(Enrollment::getProgressPercent).average().orElse(0);
        Map<String, Course> byId = new HashMap<>();
        mine.forEach(course -> byId.put(course.getId(), course));
        Map<String, String> names = new HashMap<>();
        List<LearnerRow> recent = joined.stream()
                .sorted(Comparator.comparing(Enrollment::getEnrolledAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(item -> {
                    User user = users.findById(item.getStudentId()).orElse(null);
                    String name = user == null ? "Student" : user.getFullName();
                    String email = user == null ? null : user.getEmail();
                    Course course = byId.get(item.getCourseId());
                    String title = course == null ? "Course" : course.getTitle();
                    String lastLessonTitle = course == null ? null : CourseFinder.lessonTitle(course, item.getLastLessonId());
                    return new LearnerRow(name, name, email, email, title, item.getCourseId(),
                            item.getProgressPercent(), item.getProgressPercent(),
                            item.getCompletedLessonCount(), item.getCompletedLessonCount(), item.getCountableLessonCount(),
                            item.getLastLessonId(), lastLessonTitle, lastLessonTitle, item.getEnrolledAt());
                }).toList();
        List<CourseCard> recentCourses = mine.stream()
                .sorted(Comparator.comparing(Course::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(4)
                .map(course -> cards.card(course, null))
                .toList();
        return new InstructorDashboard(mine.size(), published, draft, pending, rejected, archived,
                joined.size(), completed, Math.round(average * 10.0) / 10.0, ready, lessons, recentCourses, recent);
    }

    public List<LearnerRow> learners(AuthUser actor, String courseId) {
        Course course = owned(actor, courseId);
        return enrollments.findByCourseId(courseId).stream()
                .sorted(Comparator.comparing(Enrollment::getEnrolledAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(item -> {
                    User student = users.findById(item.getStudentId()).orElse(null);
                    String name = student == null ? "Student" : student.getFullName();
                    String email = student == null ? null : student.getEmail();
                    String lastLessonTitle = CourseFinder.lessonTitle(course, item.getLastLessonId());
                    return new LearnerRow(name, name, email, email, course.getTitle(), course.getId(),
                            item.getProgressPercent(), item.getProgressPercent(),
                            item.getCompletedLessonCount(), item.getCompletedLessonCount(), item.getCountableLessonCount(),
                            item.getLastLessonId(), lastLessonTitle, lastLessonTitle, item.getEnrolledAt());
                })
                .toList();
    }

    public Course owned(AuthUser actor, String courseId) {
        Course course = require(courseId);
        if (!course.getInstructorId().equals(actor.id())) {
            throw Errors.forbidden("You can edit only your own course.");
        }
        return course;
    }

    private CourseDetail detailForOwner(Course course) {
        User instructor = users.findById(course.getInstructorId()).orElse(null);
        return cards.detail(course, instructor, null, true);
    }

    private void applyWrite(Course course, CourseWriteRequest request) {
        Category category = categories.findById(request.categoryId()).filter(Category::isActive)
                .orElseThrow(() -> Errors.bad("BAD_CATEGORY", "Choose an active category."));
        if (request.domain() != category.getDomain() && category.getParentId() == null) {
            throw Errors.bad("BAD_CATEGORY", "The category does not match the technical classification.");
        }
        if (category.getParentId() != null) {
            Category parent = categories.findById(category.getParentId()).orElseThrow(() -> Errors.bad("BAD_CATEGORY", "The parent category is missing."));
            if (request.domain() != parent.getDomain()) {
                throw Errors.bad("BAD_CATEGORY", "The category does not match the technical classification.");
            }
            course.setSubcategoryId(category.getId());
            course.setCategoryId(parent.getId());
            course.setCategoryName(parent.getName() + " / " + category.getName());
        } else {
            course.setCategoryId(category.getId());
            course.setSubcategoryId(blank(request.subcategoryId()));
            course.setCategoryName(category.getName());
        }
        course.setTitle(request.title().trim());
        course.setShortDescription(request.shortDescription().trim());
        course.setDescription(request.description().trim());
        course.setDomain(request.domain());
        course.setDifficulty(request.difficulty());
        course.setLanguage(request.language().trim());
        course.setLearningObjectives(limit(request.learningObjectives(), 12));
        course.setPrerequisites(limit(request.prerequisites(), 12));
        course.setTargetAudience(limit(request.targetAudience(), 8));
        course.setTags(limit(request.tags(), 12));
        course.setSkills(limit(request.skills(), 12));
    }

    private void applyLesson(Course.Lesson lesson, LessonRequest request) {
        lesson.setTitle(request.title().trim());
        lesson.setDescription(blank(request.description()));
        lesson.setObjectives(limit(request.objectives(), 8));
        lesson.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
        lesson.setAccess(request.access() == null ? LessonAccess.ENROLLED_ONLY : request.access());
        lesson.setResources(request.resources() == null ? new ArrayList<>() : request.resources().stream().limit(8).map(link -> {
            Course.ResourceLink resource = new Course.ResourceLink();
            resource.setTitle(link.label().trim());
            resource.setUrl(link.url().trim());
            resource.setType("LINK");
            return resource;
        }).toList());
    }

    private String uniqueSlug(String base, String currentId) {
        String candidate = base;
        int suffix = 2;
        while (true) {
            Course existing = courses.findBySlug(candidate).orElse(null);
            if (existing == null || existing.getId().equals(currentId)) return candidate;
            candidate = base + "-" + suffix++;
        }
    }

    private static int nextOrder(List<Integer> orders) {
        return orders.stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    private static void touch(Course course) {
        course.setUpdatedAt(Instant.now());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> limit(List<String> values, int max) {
        if (values == null) return new ArrayList<>();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().limit(max).toList();
    }

    private static int count(List<Course> courses, CourseStatus status) {
        return (int) courses.stream().filter(course -> course.getStatus() == status).count();
    }
}

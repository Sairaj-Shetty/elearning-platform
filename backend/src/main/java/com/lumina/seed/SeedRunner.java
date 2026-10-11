package com.lumina.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.config.AppProperties;
import com.lumina.domain.AuditLog;
import com.lumina.domain.Category;
import com.lumina.domain.Course;
import com.lumina.domain.Enrollment;
import com.lumina.domain.Enums.AccountStatus;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.Difficulty;
import com.lumina.domain.Enums.Domain;
import com.lumina.domain.Enums.EnrollmentStatus;
import com.lumina.domain.Enums.EventType;
import com.lumina.domain.Enums.LessonAccess;
import com.lumina.domain.Enums.LessonStatus;
import com.lumina.domain.Enums.ProgressStatus;
import com.lumina.domain.Enums.ReportStatus;
import com.lumina.domain.Enums.ReportTarget;
import com.lumina.domain.Enums.Role;
import com.lumina.domain.Enums.SectionStatus;
import com.lumina.domain.Enums.VideoStatus;
import com.lumina.domain.LearningEvent;
import com.lumina.domain.LessonProgress;
import com.lumina.domain.Notification;
import com.lumina.domain.Report;
import com.lumina.domain.Review;
import com.lumina.domain.User;
import com.lumina.media.MediaStorage;
import com.lumina.repo.AuditLogRepository;
import com.lumina.repo.CategoryRepository;
import com.lumina.repo.CourseRepository;
import com.lumina.repo.EnrollmentRepository;
import com.lumina.repo.LearningEventRepository;
import com.lumina.repo.LessonProgressRepository;
import com.lumina.repo.NotificationRepository;
import com.lumina.repo.ReportRepository;
import com.lumina.repo.ReviewRepository;
import com.lumina.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Order(20)
public class SeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final PasswordEncoder encoder;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final LessonProgressRepository progress;
    private final LearningEventRepository events;
    private final ReviewRepository reviews;
    private final ReportRepository reports;
    private final NotificationRepository notifications;
    private final AuditLogRepository auditLogs;
    private final MediaStorage storage;

    public SeedRunner(AppProperties props, ObjectMapper mapper, PasswordEncoder encoder, UserRepository users,
                      CategoryRepository categories, CourseRepository courses, EnrollmentRepository enrollments,
                      LessonProgressRepository progress, LearningEventRepository events, ReviewRepository reviews,
                      ReportRepository reports, NotificationRepository notifications, AuditLogRepository auditLogs,
                      MediaStorage storage) {
        this.props = props;
        this.mapper = mapper;
        this.encoder = encoder;
        this.users = users;
        this.categories = categories;
        this.courses = courses;
        this.enrollments = enrollments;
        this.progress = progress;
        this.events = events;
        this.reviews = reviews;
        this.reports = reports;
        this.notifications = notifications;
        this.auditLogs = auditLogs;
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!props.seed().enabled()) {
            log.info("Seed disabled. Set SEED_ENABLED=true for local demo data.");
            return;
        }
        Map<String, User> people = seedUsers();
        Map<String, Category> bySlug = seedCategories();
        JsonNode root = mapper.readTree(new ClassPathResource("seed/catalog.json").getInputStream());
        int created = 0;
        for (JsonNode node : root.get("courses")) {
            if (courses.findBySlug(node.get("slug").asText()).isEmpty()) {
                courses.save(courseFrom(node, people, bySlug));
                created++;
            }
        }
        attachSampleVideos();
        seedActivity(people);
        log.info("Seed complete. New courses inserted: {}. Demo password is only for local accounts.", created);
    }

    private Map<String, User> seedUsers() {
        Map<String, User> people = new HashMap<>();
        people.put("admin", user("admin@lumina.local", "Lumina Admin", Role.ADMIN, "Keeps the catalog honest.", null));
        people.put("meera", instructor("meera@lumina.local", "Meera Iyer", "Backend engineer teaching practical Java and databases",
                "Meera has spent eight years building Java services and now teaches the parts students actually need for a project: classes, APIs, and data that survives a restart.",
                List.of("Java", "Spring Boot", "MongoDB", "SQL"),
                List.of("B.Tech Computer Science", "8 years shipping backend services")));
        people.put("arjun", instructor("arjun@lumina.local", "Arjun Rao", "Machine learning engineer focused on applied AI",
                "Arjun teaches AI without the costume. His courses separate rules, models, and generated text, and they say when a number is only a classroom result.",
                List.of("Machine learning", "Python", "Generative AI", "Cloud"),
                List.of("M.Tech in Computer Science", "Applied ML engineer")));
        people.put("elena", instructor("elena@lumina.local", "Elena Voss", "Product designer and visual storyteller",
                "Elena designs screens and pictures that can be explained. She cares more about hierarchy and light than about a new font.",
                List.of("UI design", "Photography", "Graphic design"),
                List.of("Design mentor", "Portfolio reviewer")));
        people.put("kabir", instructor("kabir@lumina.local", "Kabir Menon", "Communication coach for students and new managers",
                "Kabir helps students say the point first. His courses are short on slogans and long on sentences you can actually use in a viva.",
                List.of("Communication", "Public speaking", "Career coaching"),
                List.of("Communication coach", "Former campus placement trainer")));
        people.put("arya", student("arya@lumina.local", "Arya Shah", "Learning backend development in the final year.",
                List.of("Java", "Spring Boot", "databases"), List.of("Java"),
                List.of("Become a Java backend developer"), List.of("programming", "databases")));
        people.put("rohan", student("rohan@lumina.local", "Rohan Das", "Collecting a small design portfolio.",
                List.of("UI", "photography"), List.of("Figma"),
                List.of("Build a design portfolio"), List.of("design-tech", "creative")));
        people.put("nina", student("nina@lumina.local", "Nina Joseph", "Practicing explanations for interviews.",
                List.of("communication", "python"), List.of("Python"),
                List.of("Explain projects clearly"), List.of("communication")));
        people.put("dev", student("dev@lumina.local", "Dev Patel", "Wants a calmer deployment story.",
                List.of("docker", "security"), List.of("Docker"),
                List.of("Deploy a small service"), List.of("cloud-security")));
        people.put("sam", student("sam@lumina.local", "Sam Iqbal", "Curious about machine learning without the hype.",
                List.of("machine learning", "data"), List.of("Python"),
                List.of("Understand model evaluation"), List.of("data-ai")));
        people.put("leela", student("leela@lumina.local", "Leela Nair", "Preparing a project talk.",
                List.of("public speaking"), List.of("presentation"),
                List.of("Give a six-minute demo"), List.of("communication", "career")));
        people.replaceAll((key, user) -> users.findByEmailIgnoreCase(user.getEmail()).orElseGet(() -> users.save(user)));
        return people;
    }

    private User user(String email, String name, Role role, String bio, String headline) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(props.seed().password()));
        user.setFullName(name);
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        user.setBio(bio);
        user.setHeadline(headline);
        user.setSeedData(true);
        user.setCreatedAt(Instant.now().minus(14, ChronoUnit.DAYS));
        user.setUpdatedAt(user.getCreatedAt());
        return user;
    }

    private User instructor(String email, String name, String headline, String bio, List<String> skills, List<String> qualifications) {
        User user = user(email, name, Role.INSTRUCTOR, bio, headline);
        user.setSkills(skills);
        user.setQualifications(qualifications);
        user.setExperience(bio);
        return user;
    }

    private User student(String email, String name, String bio, List<String> interests, List<String> skills, List<String> goals, List<String> preferredSlugs) {
        User user = user(email, name, Role.STUDENT, bio, null);
        user.setInterests(interests);
        user.setSkills(skills);
        user.setLearningGoals(goals);
        user.setPreferredCategoryIds(new ArrayList<>(preferredSlugs));
        return user;
    }

    private Map<String, Category> seedCategories() throws Exception {
        JsonNode root = mapper.readTree(new ClassPathResource("seed/catalog.json").getInputStream());
        Map<String, Category> bySlug = new HashMap<>();
        int order = 1;
        for (JsonNode parentNode : root.get("categories")) {
            Category parent = categories.findBySlug(parentNode.get("slug").asText()).orElseGet(Category::new);
            parent.setName(parentNode.get("name").asText());
            parent.setSlug(parentNode.get("slug").asText());
            parent.setDomain(Domain.valueOf(parentNode.get("domain").asText()));
            parent.setDescription(parentNode.get("description").asText());
            parent.setActive(true);
            parent.setOrder(order++);
            parent.setSeedData(true);
            parent = categories.save(parent);
            bySlug.put(parent.getSlug(), parent);
            for (JsonNode childNode : parentNode.get("children")) {
                Category child = categories.findBySlug(childNode.get("slug").asText()).orElseGet(Category::new);
                child.setName(childNode.get("name").asText());
                child.setSlug(childNode.get("slug").asText());
                child.setDomain(parent.getDomain());
                child.setParentId(parent.getId());
                child.setDescription(childNode.path("description").asText(""));
                child.setActive(true);
                child.setOrder(order++);
                child.setSeedData(true);
                child = categories.save(child);
                bySlug.put(child.getSlug(), child);
            }
        }
        return bySlug;
    }

    private Course courseFrom(JsonNode node, Map<String, User> people, Map<String, Category> bySlug) {
        User instructor = people.get(node.get("instructor").asText());
        Category category = bySlug.get(node.get("category").asText());
        Category parent = category.getParentId() == null ? category : categories.findById(category.getParentId()).orElse(category);
        Course course = new Course();
        course.setInstructorId(instructor.getId());
        course.setInstructorName(instructor.getFullName());
        course.setCategoryId(parent.getId());
        course.setSubcategoryId(category.getId().equals(parent.getId()) ? null : category.getId());
        course.setCategoryName(category.getId().equals(parent.getId()) ? category.getName() : parent.getName() + " / " + category.getName());
        course.setTitle(node.get("title").asText());
        course.setSlug(node.get("slug").asText());
        course.setShortDescription(node.get("shortDescription").asText());
        course.setDescription(node.get("description").asText());
        course.setDomain(Domain.valueOf(node.get("domain").asText()));
        course.setDifficulty(Difficulty.valueOf(node.get("difficulty").asText()));
        course.setLanguage(node.path("language").asText("English"));
        course.setLearningObjectives(texts(node.get("learningObjectives")));
        course.setPrerequisites(texts(node.get("prerequisites")));
        course.setTargetAudience(texts(node.get("targetAudience")));
        course.setTags(texts(node.get("tags")));
        course.setSkills(texts(node.get("skills")));
        course.setStatus(CourseStatus.valueOf(node.path("status").asText("PUBLISHED")));
        course.setRejectionReason(node.path("rejectionReason").isMissingNode() ? null : node.path("rejectionReason").asText(null));
        course.setSeedData(true);
        course.setCreatedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        course.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        if (course.getStatus() == CourseStatus.PUBLISHED) course.setPublishedAt(course.getUpdatedAt());
        if (course.getStatus() == CourseStatus.PENDING_REVIEW) course.setSubmittedAt(course.getUpdatedAt());
        if (course.getStatus() == CourseStatus.REJECTED) {
            course.setReviewedAt(course.getUpdatedAt());
            course.setSubmittedAt(course.getUpdatedAt().minus(1, ChronoUnit.DAYS));
        }
        int sectionOrder = 1;
        for (JsonNode sectionNode : node.get("sections")) {
            Course.Section section = new Course.Section();
            section.setSectionId(UUID.randomUUID().toString());
            section.setTitle(sectionNode.get("title").asText());
            section.setDescription(sectionNode.get("description").asText());
            section.setOrder(sectionOrder++);
            section.setStatus(SectionStatus.ACTIVE);
            int lessonOrder = 1;
            for (JsonNode lessonNode : sectionNode.get("lessons")) {
                Course.Lesson lesson = new Course.Lesson();
                lesson.setLessonId(UUID.randomUUID().toString());
                lesson.setTitle(lessonNode.get("title").asText());
                lesson.setDescription(lessonNode.get("description").asText());
                lesson.setOrder(lessonOrder++);
                lesson.setEstimatedDurationMinutes(lessonNode.path("minutes").asInt(12));
                lesson.setAccess(LessonAccess.valueOf(lessonNode.path("access").asText("ENROLLED_ONLY")));
                lesson.setStatus(LessonStatus.ACTIVE);
                lesson.setVideo(new Course.Video());
                if (lessonNode.has("resources")) {
                    for (JsonNode resourceNode : lessonNode.get("resources")) {
                        Course.ResourceLink link = new Course.ResourceLink();
                        link.setTitle(resourceNode.get("title").asText());
                        link.setUrl(resourceNode.get("url").asText());
                        link.setType(resourceNode.path("type").asText("LINK"));
                        lesson.getResources().add(link);
                    }
                }
                section.getLessons().add(lesson);
            }
            course.getSections().add(section);
        }
        course.recomputeDuration();
        course.setStats(new Course.CourseStats());
        return course;
    }

    private void attachSampleVideos() throws Exception {
        ClassPathResource sample = new ClassPathResource("sample/sample-lesson.mp4");
        for (String slug : List.of("java-and-spring-boot", "python-programming")) {
            Course course = courses.findBySlug(slug).orElse(null);
            if (course == null) continue;
            boolean changed = false;
            for (Course.Section section : course.getSections()) {
                Course.Lesson lesson = section.getLessons().stream().filter(item -> item.getStatus() == LessonStatus.ACTIVE).findFirst().orElse(null);
                if (lesson == null || (lesson.getVideo() != null && lesson.getVideo().getStatus() == VideoStatus.READY)) continue;
                String key = "videos/" + course.getId() + "/" + lesson.getLessonId() + "/sample-lesson.mp4";
                try (InputStream in = sample.getInputStream()) {
                    storage.store(key, in);
                }
                Course.Video video = new Course.Video();
                video.setStatus(VideoStatus.READY);
                video.setStorageKey(key);
                video.setOriginalFilename("sample-lesson.mp4");
                video.setMimeType("video/mp4");
                video.setSizeBytes(sample.contentLength());
                video.setUploadedAt(Instant.now().minus(2, ChronoUnit.DAYS));
                lesson.setVideo(video);
                changed = true;
            }
            if (changed) courses.save(course);
        }
    }

    private void seedActivity(Map<String, User> people) {
        linkPreferredCategories(people);
        enroll(people.get("arya"), "java-and-spring-boot", 3);
        enroll(people.get("arya"), "python-programming", 2);
        enroll(people.get("arya"), "mongodb", 1);
        enroll(people.get("arya"), "data-structures-and-algorithms", 0);
        enroll(people.get("rohan"), "ui-ux-design", 2);
        enroll(people.get("rohan"), "photography", 1);
        enroll(people.get("nina"), "spoken-english", 1);
        enroll(people.get("dev"), "devops-and-docker", 1);
        enroll(people.get("dev"), "cybersecurity", 0);
        enroll(people.get("sam"), "machine-learning", 1);
        enroll(people.get("sam"), "generative-ai-and-llms", 0);
        enroll(people.get("leela"), "public-speaking", 1);
        seedProgress(people.get("arya"), "java-and-spring-boot");
        seedReview(people.get("arya"), "java-and-spring-boot", 5, "The request-path section made the backend layers easier to explain in a viva.");
        seedReview(people.get("rohan"), "ui-ux-design", 4, "Useful reminders about empty states. I still want more examples of mobile course cards.");
        seedReport(people.get("rohan"));
        seedAudit(people.get("admin"));
        seedNotification(people.get("elena"));
    }

    private void linkPreferredCategories(Map<String, User> people) {
        for (User student : List.of(people.get("arya"), people.get("rohan"), people.get("nina"), people.get("dev"), people.get("sam"), people.get("leela"))) {
            if (student.getPreferredCategoryIds() == null || student.getPreferredCategoryIds().isEmpty()) continue;
            if (student.getPreferredCategoryIds().get(0).length() > 20) continue;
            List<String> ids = new ArrayList<>();
            for (String slug : student.getPreferredCategoryIds()) {
                categories.findBySlug(slug).ifPresent(category -> ids.add(category.getId()));
            }
            student.setPreferredCategoryIds(ids);
            users.save(student);
        }
    }

    private void enroll(User student, String slug, int daysAgo) {
        Course course = courses.findBySlug(slug).orElse(null);
        if (course == null || student == null) return;
        if (enrollments.findByStudentIdAndCourseId(student.getId(), course.getId()).isPresent()) return;
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(student.getId());
        enrollment.setCourseId(course.getId());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setEnrolledAt(Instant.now().minus(daysAgo + 1, ChronoUnit.DAYS));
        enrollment.setLastAccessedAt(enrollment.getEnrolledAt());
        enrollment.setCurriculumVersionAtEnrollment(course.getCurriculumVersion());
        enrollment.setCountableLessonCount(course.countableLessonCount());
        enrollment.setSeedData(true);
        enrollments.save(enrollment);
        course.getStats().setEnrollmentCount(course.getStats().getEnrollmentCount() + 1);
        courses.save(course);
        LearningEvent event = new LearningEvent();
        event.setStudentId(student.getId());
        event.setCourseId(course.getId());
        event.setEventType(EventType.ENROLLED);
        event.setOccurredAt(enrollment.getEnrolledAt());
        event.setSeedData(true);
        events.save(event);
    }

    private void seedProgress(User student, String slug) {
        Course course = courses.findBySlug(slug).orElse(null);
        if (course == null || !progress.findByStudentIdAndCourseId(student.getId(), course.getId()).isEmpty()) return;
        List<Course.Lesson> ready = course.getSections().stream()
                .flatMap(section -> section.getLessons().stream())
                .filter(lesson -> lesson.getVideo() != null && lesson.getVideo().getStatus() == VideoStatus.READY)
                .toList();
        if (ready.isEmpty()) return;
        completeLesson(student, course, ready.get(0), Instant.now().minus(1, ChronoUnit.DAYS));
        if (ready.size() > 1) {
            LessonProgress current = baseProgress(student, course, ready.get(1));
            current.setDurationSeconds(1);
            current.setAccumulatedWatchSeconds(0.4);
            current.setLastPositionSeconds(0.4);
            current.setMaxWatchedPercent(40);
            current.setStatus(ProgressStatus.IN_PROGRESS);
            current.setLastWatchedAt(Instant.now().minus(2, ChronoUnit.HOURS));
            progress.save(current);
            Enrollment enrollment = enrollments.findByStudentIdAndCourseId(student.getId(), course.getId()).orElseThrow();
            enrollment.setLastLessonId(ready.get(1).getLessonId());
            enrollment.setLastPositionSeconds(0.4);
            enrollment.setLastAccessedAt(current.getLastWatchedAt());
            long completed = progress.countByStudentIdAndCourseIdAndStatus(student.getId(), course.getId(), ProgressStatus.COMPLETED);
            enrollment.setCompletedLessonCount((int) completed);
            enrollment.setCountableLessonCount(course.countableLessonCount());
            enrollment.setProgressPercent(course.countableLessonCount() == 0 ? 0 : Math.round(completed * 1000.0 / course.countableLessonCount()) / 10.0);
            enrollments.save(enrollment);
        }
    }

    private void completeLesson(User student, Course course, Course.Lesson lesson, Instant when) {
        LessonProgress item = baseProgress(student, course, lesson);
        item.setDurationSeconds(1);
        item.setAccumulatedWatchSeconds(1);
        item.setLastPositionSeconds(1);
        item.setMaxWatchedPercent(100);
        item.setStatus(ProgressStatus.COMPLETED);
        item.setCompletionMethod("WATCH_THRESHOLD");
        item.setCompletedAt(when);
        item.setLastWatchedAt(when);
        item.setHighestMilestone(100);
        progress.save(item);
        LearningEvent opened = new LearningEvent();
        opened.setStudentId(student.getId());
        opened.setCourseId(course.getId());
        opened.setLessonId(lesson.getLessonId());
        opened.setEventType(EventType.LESSON_OPENED);
        opened.setOccurredAt(when.minus(20, ChronoUnit.MINUTES));
        opened.setSeedData(true);
        events.save(opened);
        LearningEvent completed = new LearningEvent();
        completed.setStudentId(student.getId());
        completed.setCourseId(course.getId());
        completed.setLessonId(lesson.getLessonId());
        completed.setEventType(EventType.LESSON_COMPLETED);
        completed.setOccurredAt(when);
        completed.setSeedData(true);
        events.save(completed);
    }

    private LessonProgress baseProgress(User student, Course course, Course.Lesson lesson) {
        LessonProgress item = new LessonProgress();
        item.setStudentId(student.getId());
        item.setCourseId(course.getId());
        item.setLessonId(lesson.getLessonId());
        item.setFirstOpenedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        item.setOpenedEventWritten(true);
        item.setSeedData(true);
        return item;
    }

    private void seedReview(User student, String slug, int rating, String comment) {
        Course course = courses.findBySlug(slug).orElse(null);
        if (course == null || reviews.findByStudentIdAndCourseId(student.getId(), course.getId()).isPresent()) return;
        Review review = new Review();
        review.setStudentId(student.getId());
        review.setStudentName(student.getFullName());
        review.setCourseId(course.getId());
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(Instant.now().minus(12, ChronoUnit.HOURS));
        review.setSeedData(true);
        reviews.save(review);
        course.getStats().setReviewCount(course.getStats().getReviewCount() + 1);
        course.getStats().setAverageRating((double) rating);
        courses.save(course);
    }

    private void seedReport(User reporter) {
        if (reports.count() > 0) return;
        Course course = courses.findBySlug("photography").orElse(null);
        if (course == null) return;
        Report report = new Report();
        report.setReporterId(reporter.getId());
        report.setReporterName(reporter.getFullName());
        report.setTargetType(ReportTarget.COURSE);
        report.setTargetId(course.getId());
        report.setCourseId(course.getId());
        report.setReason("Description mismatch");
        report.setDescription("Sample report: the course description should mention that a phone camera is enough, so students do not think a paid camera is required.");
        report.setStatus(ReportStatus.OPEN);
        report.setCreatedAt(Instant.now().minus(6, ChronoUnit.HOURS));
        report.setSeedData(true);
        reports.save(report);
    }

    private void seedAudit(User admin) {
        Course course = courses.findBySlug("3d-modeling-and-animation").orElse(null);
        if (course == null || auditLogs.existsByActionAndTargetId("COURSE_REJECTED", course.getId())) return;
        AuditLog logEntry = new AuditLog();
        logEntry.setActorId(admin.getId());
        logEntry.setActorRole(Role.ADMIN);
        logEntry.setAction("COURSE_REJECTED");
        logEntry.setTargetType("COURSE");
        logEntry.setTargetId(course.getId());
        logEntry.setDetails(course.getRejectionReason());
        logEntry.setCreatedAt(course.getReviewedAt() == null ? Instant.now().minus(1, ChronoUnit.DAYS) : course.getReviewedAt());
        logEntry.setSeedData(true);
        auditLogs.save(logEntry);
    }

    private void seedNotification(User elena) {
        if (!notifications.findByUserIdOrderByCreatedAtDesc(elena.getId()).isEmpty()) return;
        Notification notification = new Notification();
        notification.setUserId(elena.getId());
        notification.setType("COURSE_REJECTED");
        notification.setTitle("Course needs changes");
        notification.setBody("3D Modeling and Animation was rejected. Read the reason, edit the draft, and submit again.");
        notification.setLink("/teach");
        notification.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        notifications.save(notification);
    }

    private static List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(item -> values.add(item.asText()));
        return values;
    }
}

package com.lumina.api;

import java.time.Instant;
import java.util.List;

public final class Views {
    private Views() {}

    public record HealthView(String status, String mongo, String database) {}

    public record PublicConfig(boolean seedEnabled, boolean aiConfigured, String platformName, boolean replicaSet) {}

    public record DemoAccount(String role, String email, String password, String name, String note) {}

    public record AuthView(String accessToken, int expiresInSeconds, UserView user) {}

    public record UserView(
            String id, String email, String fullName, String role, String status, String avatarUrl,
            String bio, String headline, String experience,
            List<String> interests, List<String> skills, List<String> learningGoals, List<String> preferredCategoryIds,
            List<String> qualifications, List<LinkView> portfolioLinks,
            Instant createdAt, Instant lastLoginAt, boolean seedData
    ) {}

    public record LinkView(String label, String url) {}

    public record CategoryView(
            String id, String name, String slug, String domain, String parentId, String description,
            boolean active, int order, long courseCount
    ) {}

    public record CourseCard(
            String id, String slug, String title, String shortDescription,
            String instructorId, String instructorName, String thumbnailUrl,
            String domain, String difficulty, String language,
            String categoryId, String categoryName, String subcategoryId,
            int estimatedDurationMinutes, int lessonCount, int enrollmentCount,
            Double averageRating, int reviewCount, String status,
            Double progressPercent, boolean enrolled, boolean seedData, boolean contentDisabled
    ) {}

    public record ResourceView(String title, String url, String type) {}

    public record LessonView(
            String lessonId, String title, String description, List<String> objectives, int order,
            int estimatedDurationMinutes, String access, String status, String videoStatus,
            Integer videoDurationSeconds, Long videoSizeBytes, List<ResourceView> resources
    ) {}

    public record SectionView(String sectionId, String title, String description, int order, String status, List<LessonView> lessons) {}

    public record ViewerState(
            boolean enrolled, Double progressPercent, String lastLessonId, String lastLessonTitle,
            Double lastPositionSeconds, int completedLessonCount, int countableLessonCount, String enrollmentStatus
    ) {}

    public record CourseDetail(
            String id, String slug, String title, String shortDescription, String description,
            String instructorId, String instructorName, String thumbnailUrl,
            String domain, String difficulty, String language,
            String categoryId, String categoryName, String subcategoryId,
            List<String> learningObjectives, List<String> prerequisites, List<String> targetAudience,
            List<String> tags, List<String> skills,
            int estimatedDurationMinutes, int lessonCount, int enrollmentCount,
            Double averageRating, int reviewCount, String status, String rejectionReason,
            boolean contentDisabled, String disabledReason, boolean seedData,
            Instant submittedAt, Instant reviewedAt, Instant publishedAt,
            List<SectionView> sections, InstructorSnippet instructor, ViewerState viewer
    ) {}

    public record InstructorSnippet(String id, String fullName, String headline, String avatarUrl) {}

    public record InstructorPublic(
            String id, String fullName, String headline, String bio, String avatarUrl,
            List<String> skills, List<String> qualifications, String experience, List<LinkView> portfolioLinks,
            List<CourseCard> courses
    ) {}

    public record EnrollmentView(
            String id, String courseId, String courseSlug, String courseTitle, String thumbnailUrl,
            String status, double progressPercent, int completedLessonCount, int countableLessonCount,
            Instant enrolledAt, Instant lastAccessedAt, String lastLessonId, String lastLessonTitle, Double lastPositionSeconds
    ) {}

    public record ProgressView(
            String lessonId, String courseId, String status, double lastPositionSeconds, double durationSeconds,
            double accumulatedWatchSeconds, double maxWatchedPercent, boolean completed, String message
    ) {}

    public record HistoryItem(
            String id, String eventType, String courseId, String courseTitle, String courseSlug,
            String lessonId, String lessonTitle, Instant occurredAt
    ) {}

    public record NoteView(
            String id, String courseId, String lessonId, String lessonTitle, String type, String body,
            Double positionSeconds, Instant createdAt
    ) {}

    public record ReviewView(String id, String studentName, int rating, String comment, Instant createdAt, boolean seedData) {}

    public record NotificationView(String id, String type, String title, String body, String link, boolean read, Instant createdAt) {}

    public record CertificateView(
            String id, String certificateId, String studentId, String studentName,
            String courseId, String courseTitle, Instant completedAt, Instant completionDate,
            Instant issuedAt, boolean seedData
    ) {}

    public record StudentDashboard(
            int enrolledCount, int completedCount, int inProgressCount, double averageProgress,
            List<EnrollmentView> continueLearning, List<HistoryItem> recentEvents
    ) {}

    public record InstructorDashboard(
            int totalCourses, int published, int draft, int pending, int rejected, int archived,
            long totalEnrollments, long completedEnrollments, double averageProgress,
            int lessonsWithVideo, int totalLessons, List<CourseCard> recentCourses, List<LearnerRow> recentEnrollments
    ) {}

    public record LearnerRow(
            String studentName, String name, String studentEmail, String email,
            String courseTitle, String courseId,
            double progressPercent, double progress,
            int completedLessonCount, int completedLessons, int countableLessonCount,
            String lastLessonId, String lastLesson, String lastLessonTitle,
            Instant enrolledAt
    ) {}

    public record AdminDashboard(
            long students, long instructors, long admins,
            java.util.Map<String, Long> coursesByStatus, long totalLessons, long readyVideos,
            long enrollments, long openReports, List<UserView> recentUsers, List<AuditView> recentAudit
    ) {}

    public record AuditView(String id, String actorId, String actorRole, String action, String targetType, String targetId, String details, Instant createdAt, boolean seedData) {}

    public record ReportView(
            String id, String reporterName, String targetType, String targetId, String courseId,
            String reason, String description, String status, String resolution, Instant createdAt, boolean seedData,
            String aiRisk, String aiSeverity, String aiExplanation
    ) {}

    public record TicketView(String url, int expiresInSeconds) {}

    public record AiText(boolean aiAvailable, String source, String message, String text) {}

    public record AssistantReply(boolean aiAvailable, String source, String message, String reply, String sessionId) {}

    public record RecommendationItem(CourseCard course, int score, List<String> reasons, String explanation, String source) {}

    public record RoadmapStageView(
            int index, String title, String description, List<String> concepts, List<CourseCard> courses,
            String practice, String milestone, boolean completed
    ) {}

    public record RoadmapView(String id, String goal, String source, String model, List<RoadmapStageView> stages, Instant createdAt) {}

    public record QuestionView(String questionId, String type, String prompt, List<String> options, int points) {}

    public record QuizView(
            String id, String courseId, String lessonId, String title, String visibility, boolean approved,
            boolean aiGenerated, List<QuestionView> questions, Instant createdAt
    ) {}

    public record AnswerView(String questionId, Boolean correct, Integer score, Integer maxScore, boolean aiEvaluated, String feedback, String explanation) {}

    public record AttemptView(
            String id, String quizId, int totalScore, int maxScore, Double percent, boolean includesApproximateScore,
            List<AnswerView> answers, Instant submittedAt
    ) {}

    public record InsightItem(String kind, String title, String detail, String courseId, String lessonId, boolean inference) {}

    public record InsightsView(boolean aiAvailable, String narrative, List<InsightItem> items) {}

    public record AssistSuggestion(
            boolean aiAvailable, String source, String message,
            String description, List<String> objectives, List<String> prerequisites,
            List<String> sectionTitles, List<String> tags, String lessonDescription
    ) {}

    public record ModerationResult(
            boolean aiAvailable, String source, String riskCategory, String severity, String explanation, boolean humanReviewRequired
    ) {}
}

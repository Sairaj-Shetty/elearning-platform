package com.lumina.domain;

public final class Enums {
    private Enums() {}

    public enum Role { STUDENT, INSTRUCTOR, ADMIN }

    public enum AccountStatus { ACTIVE, SUSPENDED }

    public enum Domain { TECHNICAL, NON_TECHNICAL }

    public enum Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

    public enum CourseStatus { DRAFT, PENDING_REVIEW, PUBLISHED, REJECTED, ARCHIVED }

    public enum LessonAccess { FREE_PREVIEW, ENROLLED_ONLY }

    public enum LessonStatus { ACTIVE, REMOVED }

    public enum SectionStatus { ACTIVE, REMOVED }

    public enum VideoStatus { NOT_UPLOADED, UPLOADING, READY, FAILED, REMOVED }

    public enum EnrollmentStatus { ACTIVE, COMPLETED, WITHDRAWN }

    public enum ProgressStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

    public enum EventType {
        ENROLLED, LESSON_OPENED, PLAYBACK_MILESTONE, LESSON_COMPLETED,
        COURSE_COMPLETED, SEARCHED, NOTE_CREATED
    }

    public enum NoteType { NOTE, BOOKMARK }

    public enum QuestionType { MCQ, TRUE_FALSE, SHORT }

    public enum QuizSource { AI, INSTRUCTOR }

    public enum QuizVisibility { PRACTICE, COURSE }

    public enum ReportTarget { COURSE, LESSON, REVIEW, USER }

    public enum ReportStatus { OPEN, UNDER_REVIEW, RESOLVED, DISMISSED }

    public enum AiFeature {
        STUDY_ASSISTANT, RECOMMENDATION, ROADMAP, QUIZ, COURSE_ASSIST, MODERATION, INSIGHTS
    }
}

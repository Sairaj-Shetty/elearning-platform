package com.lumina.domain;

import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.Difficulty;
import com.lumina.domain.Enums.Domain;
import com.lumina.domain.Enums.LessonAccess;
import com.lumina.domain.Enums.LessonStatus;
import com.lumina.domain.Enums.SectionStatus;
import com.lumina.domain.Enums.VideoStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Document("courses")
public class Course {
    @Id
    private String id;
    @Version
    private Long version;
    @Indexed
    private String instructorId;
    private String instructorName;
    @Indexed
    private String categoryId;
    private String subcategoryId;
    private String categoryName;
    private String title;
    @Indexed(unique = true)
    private String slug;
    private String shortDescription;
    private String description;
    private String thumbnailKey;
    private Domain domain;
    private Difficulty difficulty;
    private String language = "English";
    private List<String> learningObjectives = new ArrayList<>();
    private List<String> prerequisites = new ArrayList<>();
    private List<String> targetAudience = new ArrayList<>();
    private int estimatedDurationMinutes;
    private List<String> tags = new ArrayList<>();
    private List<String> skills = new ArrayList<>();
    private CourseStatus status = CourseStatus.DRAFT;
    private String rejectionReason;
    private boolean contentDisabled;
    private String disabledReason;
    private Instant submittedAt;
    private Instant reviewedAt;
    private String reviewedBy;
    private Instant publishedAt;
    private int curriculumVersion = 1;
    private List<Section> sections = new ArrayList<>();
    private CourseStats stats = new CourseStats();
    private boolean seedData;
    private Instant createdAt;
    private Instant updatedAt;

    public int activeLessonCount() {
        int count = 0;
        for (Section section : sections) {
            if (section.getStatus() != SectionStatus.ACTIVE) continue;
            for (Lesson lesson : section.getLessons()) {
                if (lesson.getStatus() == LessonStatus.ACTIVE) count++;
            }
        }
        return count;
    }

    public int countableLessonCount() {
        int count = 0;
        for (Section section : sections) {
            if (section.getStatus() != SectionStatus.ACTIVE) continue;
            for (Lesson lesson : section.getLessons()) {
                if (lesson.getStatus() == LessonStatus.ACTIVE
                        && lesson.getVideo() != null
                        && lesson.getVideo().getStatus() == VideoStatus.READY) {
                    count++;
                }
            }
        }
        return count;
    }

    public void recomputeDuration() {
        int minutes = 0;
        for (Section section : sections) {
            if (section.getStatus() != SectionStatus.ACTIVE) continue;
            for (Lesson lesson : section.getLessons()) {
                if (lesson.getStatus() == LessonStatus.ACTIVE) {
                    minutes += Math.max(lesson.getEstimatedDurationMinutes(), 0);
                }
            }
        }
        this.estimatedDurationMinutes = minutes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Section {
        private String sectionId;
        private String title;
        private String description;
        private int order;
        private SectionStatus status = SectionStatus.ACTIVE;
        private List<Lesson> lessons = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Lesson {
        private String lessonId;
        private String title;
        private String description;
        private List<String> objectives = new ArrayList<>();
        private int order;
        private int estimatedDurationMinutes;
        private LessonAccess access = LessonAccess.ENROLLED_ONLY;
        private LessonStatus status = LessonStatus.ACTIVE;
        private Video video = new Video();
        private List<ResourceLink> resources = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Video {
        private VideoStatus status = VideoStatus.NOT_UPLOADED;
        private String storageKey;
        private String originalFilename;
        private String mimeType;
        private Long sizeBytes;
        private Integer durationSeconds;
        private Instant uploadedAt;
        private String failureReason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ResourceLink {
        private String title;
        private String url;
        private String type;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CourseStats {
        private int enrollmentCount;
        private Double averageRating;
        private int reviewCount;
    }
}

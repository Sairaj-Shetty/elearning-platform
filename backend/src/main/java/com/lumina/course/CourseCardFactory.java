package com.lumina.course;

import com.lumina.api.Views.CourseCard;
import com.lumina.api.Views.CourseDetail;
import com.lumina.api.Views.InstructorSnippet;
import com.lumina.api.Views.LessonView;
import com.lumina.api.Views.ResourceView;
import com.lumina.api.Views.SectionView;
import com.lumina.api.Views.ViewerState;
import com.lumina.domain.Course;
import com.lumina.domain.Enrollment;
import com.lumina.domain.Enums.LessonStatus;
import com.lumina.domain.Enums.SectionStatus;
import com.lumina.domain.User;
import com.lumina.user.UserMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CourseCardFactory {
    public CourseCard card(Course course, Enrollment enrollment) {
        return new CourseCard(
                course.getId(),
                course.getSlug(),
                course.getTitle(),
                course.getShortDescription(),
                course.getInstructorId(),
                course.getInstructorName(),
                thumbnail(course.getThumbnailKey()),
                name(course.getDomain()),
                name(course.getDifficulty()),
                course.getLanguage(),
                course.getCategoryId(),
                course.getCategoryName(),
                course.getSubcategoryId(),
                course.getEstimatedDurationMinutes(),
                course.activeLessonCount(),
                course.getStats() == null ? 0 : course.getStats().getEnrollmentCount(),
                course.getStats() == null ? null : course.getStats().getAverageRating(),
                course.getStats() == null ? 0 : course.getStats().getReviewCount(),
                name(course.getStatus()),
                enrollment == null ? null : enrollment.getProgressPercent(),
                enrollment != null,
                course.isSeedData(),
                course.isContentDisabled()
        );
    }

    public CourseDetail detail(Course course, User instructor, Enrollment enrollment, boolean includeRemoved) {
        List<SectionView> sections = course.getSections().stream()
                .filter(section -> includeRemoved || section.getStatus() == SectionStatus.ACTIVE)
                .map(section -> new SectionView(
                        section.getSectionId(),
                        section.getTitle(),
                        section.getDescription(),
                        section.getOrder(),
                        section.getStatus().name(),
                        section.getLessons().stream()
                                .filter(lesson -> includeRemoved || lesson.getStatus() == LessonStatus.ACTIVE)
                                .map(this::lesson)
                                .toList()
                ))
                .toList();
        InstructorSnippet snippet = instructor == null ? new InstructorSnippet(course.getInstructorId(), course.getInstructorName(), null, null)
                : new InstructorSnippet(instructor.getId(), instructor.getFullName(), instructor.getHeadline(), UserMapper.avatarUrl(instructor.getAvatarKey()));
        ViewerState viewer = new ViewerState(
                enrollment != null,
                enrollment == null ? null : enrollment.getProgressPercent(),
                enrollment == null ? null : enrollment.getLastLessonId(),
                enrollment == null ? null : CourseFinder.lessonTitle(course, enrollment.getLastLessonId()),
                enrollment == null ? null : enrollment.getLastPositionSeconds(),
                enrollment == null ? 0 : enrollment.getCompletedLessonCount(),
                enrollment == null ? course.countableLessonCount() : enrollment.getCountableLessonCount(),
                enrollment == null ? null : enrollment.getStatus().name()
        );
        return new CourseDetail(
                course.getId(), course.getSlug(), course.getTitle(), course.getShortDescription(), course.getDescription(),
                course.getInstructorId(), course.getInstructorName(), thumbnail(course.getThumbnailKey()),
                name(course.getDomain()), name(course.getDifficulty()), course.getLanguage(),
                course.getCategoryId(), course.getCategoryName(), course.getSubcategoryId(),
                course.getLearningObjectives(), course.getPrerequisites(), course.getTargetAudience(),
                course.getTags(), course.getSkills(),
                course.getEstimatedDurationMinutes(), course.activeLessonCount(),
                course.getStats() == null ? 0 : course.getStats().getEnrollmentCount(),
                course.getStats() == null ? null : course.getStats().getAverageRating(),
                course.getStats() == null ? 0 : course.getStats().getReviewCount(),
                name(course.getStatus()), course.getRejectionReason(), course.isContentDisabled(), course.getDisabledReason(),
                course.isSeedData(), course.getSubmittedAt(), course.getReviewedAt(), course.getPublishedAt(),
                sections, snippet, viewer
        );
    }

    public LessonView lesson(Course.Lesson lesson) {
        return new LessonView(
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getObjectives() == null ? List.of() : lesson.getObjectives(),
                lesson.getOrder(),
                lesson.getEstimatedDurationMinutes(),
                lesson.getAccess().name(),
                lesson.getStatus().name(),
                lesson.getVideo() == null ? "NOT_UPLOADED" : lesson.getVideo().getStatus().name(),
                lesson.getVideo() == null ? null : lesson.getVideo().getDurationSeconds(),
                lesson.getVideo() == null ? null : lesson.getVideo().getSizeBytes(),
                lesson.getResources() == null ? List.of() : lesson.getResources().stream()
                        .map(resource -> new ResourceView(resource.getTitle(), resource.getUrl(), resource.getType()))
                        .toList()
        );
    }

    public static String thumbnail(String key) {
        return key == null ? null : "/api/v1/media/files/" + key;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}

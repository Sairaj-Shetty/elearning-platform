package com.lumina.api;

import com.lumina.domain.Enums.Difficulty;
import com.lumina.domain.Enums.Domain;
import com.lumina.domain.Enums.LessonAccess;
import com.lumina.domain.Enums.NoteType;
import com.lumina.domain.Enums.ReportTarget;
import com.lumina.domain.Enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class Requests {
    private Requests() {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 80) String fullName,
            @NotNull Role role
    ) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record ProfileRequest(
            @NotBlank @Size(max = 80) String fullName,
            @Size(max = 120) String headline,
            @Size(max = 2000) String bio,
            @Size(max = 2000) String experience,
            List<String> interests,
            List<String> skills,
            List<String> learningGoals,
            List<String> preferredCategoryIds,
            List<String> qualifications,
            List<LinkRequest> portfolioLinks
    ) {}

    public record LinkRequest(@NotBlank @Size(max = 40) String label, @NotBlank @Size(max = 300) String url) {}

    public record CategoryRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 400) String description,
            Domain domain,
            String parentId
    ) {}

    public record CourseWriteRequest(
            @NotBlank @Size(max = 140) String title,
            @NotBlank @Size(max = 280) String shortDescription,
            @NotBlank @Size(max = 8000) String description,
            @NotBlank String categoryId,
            String subcategoryId,
            @NotNull Domain domain,
            @NotNull Difficulty difficulty,
            @NotBlank @Size(max = 40) String language,
            @NotEmpty List<@NotBlank @Size(max = 240) String> learningObjectives,
            List<String> prerequisites,
            List<String> targetAudience,
            List<String> tags,
            List<String> skills
    ) {}

    public record SectionRequest(@NotBlank @Size(max = 140) String title, @Size(max = 1000) String description) {}

    public record LessonRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 4000) String description,
            List<String> objectives,
            @Min(1) @Max(600) int estimatedDurationMinutes,
            @NotNull LessonAccess access,
            List<LinkRequest> resources
    ) {}

    public record ReorderRequest(@NotEmpty List<String> orderedIds) {}

    public record RejectRequest(@NotBlank @Size(min = 10, max = 1000) String reason) {}

    public record DisableRequest(boolean disabled, @Size(max = 500) String reason) {}

    public record StatusRequest(@NotNull com.lumina.domain.Enums.AccountStatus status) {}

    public record RoleRequest(@NotNull Role role) {}

    public record EnrollRequest(@NotBlank String courseId) {}

    public record ProgressRequest(
            @NotBlank String courseId,
            @NotBlank String lessonId,
            @Min(0) double positionSeconds,
            @Min(0) double durationSeconds
    ) {}

    public record CompleteRequest(@NotBlank String courseId, @NotBlank String lessonId) {}

    public record NoteRequest(
            @NotBlank String courseId,
            @NotBlank String lessonId,
            @NotNull NoteType type,
            @NotBlank @Size(max = 4000) String body,
            Double positionSeconds
    ) {}

    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String comment) {}

    public record ReportRequest(
            @NotNull ReportTarget targetType,
            @NotBlank String targetId,
            String courseId,
            @NotBlank @Size(max = 80) String reason,
            @Size(max = 2000) String description
    ) {}

    public record ResolveReportRequest(@NotNull com.lumina.domain.Enums.ReportStatus status, @Size(max = 1000) String resolution) {}

    public record AssistantRequest(String courseId, String lessonId, @NotBlank @Size(max = 2000) String message) {}

    public record RoadmapRequest(@NotBlank @Size(max = 300) String goal) {}

    public record QuizGenerateRequest(@NotBlank String courseId, String lessonId, @Min(1) @Max(8) Integer count) {}

    public record AnswerRequest(String questionId, Integer selectedIndex, String textAnswer) {}

    public record AttemptRequest(@NotEmpty List<AnswerRequest> answers) {}

    public record AssistRequest(
            @NotBlank String task,
            @Size(max = 140) String title,
            @Size(max = 8000) String description,
            String domain,
            String difficulty,
            String categoryName,
            String existingOutline
    ) {}

    public record ModerateRequest(@Size(max = 200) String title, @Size(max = 8000) String description, @Size(max = 4000) String extraText) {}

    public record TicketRequest(@NotBlank String courseId, @NotBlank String lessonId) {}
}

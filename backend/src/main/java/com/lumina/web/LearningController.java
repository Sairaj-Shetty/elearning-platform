package com.lumina.web;

import com.lumina.api.Requests.CompleteRequest;
import com.lumina.api.Requests.EnrollRequest;
import com.lumina.api.Requests.NoteRequest;
import com.lumina.api.Requests.ProfileRequest;
import com.lumina.api.Requests.ProgressRequest;
import com.lumina.api.Requests.ReportRequest;
import com.lumina.api.Requests.ReviewRequest;
import com.lumina.api.Views.EnrollmentView;
import com.lumina.api.Views.HistoryItem;
import com.lumina.api.Views.NoteView;
import com.lumina.api.Views.NotificationView;
import com.lumina.api.Views.ProgressView;
import com.lumina.api.Views.ReviewView;
import com.lumina.api.Views.StudentDashboard;
import com.lumina.api.Views.UserView;
import com.lumina.common.ApiResponse;
import com.lumina.common.Errors;
import com.lumina.common.Pages.PageData;
import com.lumina.domain.Notification;
import com.lumina.learning.LearningService;
import com.lumina.repo.NotificationRepository;
import com.lumina.security.SecurityUtils;
import com.lumina.user.UserService;
import com.lumina.admin.AdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Learning")
public class LearningController {
    private final LearningService learning;
    private final UserService users;
    private final AdminService admin;
    private final NotificationRepository notifications;

    public LearningController(LearningService learning, UserService users, AdminService admin, NotificationRepository notifications) {
        this.learning = learning;
        this.users = users;
        this.admin = admin;
        this.notifications = notifications;
    }

    @PutMapping("/me")
    public ApiResponse<UserView> updateProfile(@Valid @RequestBody ProfileRequest request) {
        return ApiResponse.ok(users.update(SecurityUtils.requireUser(), request));
    }

    @GetMapping("/me/dashboard")
    public ApiResponse<StudentDashboard> dashboard() {
        return ApiResponse.ok(learning.dashboard(SecurityUtils.requireUser()));
    }

    @PostMapping("/enrollments")
    public ApiResponse<EnrollmentView> enroll(@Valid @RequestBody EnrollRequest request) {
        return ApiResponse.ok(learning.enroll(SecurityUtils.requireUser(), request.courseId()));
    }

    @GetMapping("/me/enrollments")
    public ApiResponse<List<EnrollmentView>> enrollments() {
        return ApiResponse.ok(learning.myEnrollments(SecurityUtils.requireUser()));
    }

    @PutMapping("/me/progress")
    public ApiResponse<ProgressView> progress(@Valid @RequestBody ProgressRequest request) {
        return ApiResponse.ok(learning.updateProgress(SecurityUtils.requireUser(), request));
    }

    @PostMapping("/me/progress/complete")
    public ApiResponse<ProgressView> complete(@Valid @RequestBody CompleteRequest request) {
        return ApiResponse.ok(learning.complete(SecurityUtils.requireUser(), request));
    }

    @GetMapping("/me/history")
    public ApiResponse<PageData<HistoryItem>> history(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(learning.history(SecurityUtils.requireUser(), page, size));
    }

    @GetMapping("/me/notes")
    public ApiResponse<List<NoteView>> notes(@RequestParam String courseId) {
        return ApiResponse.ok(learning.notes(SecurityUtils.requireUser(), courseId));
    }

    @PostMapping("/me/notes")
    public ApiResponse<NoteView> addNote(@Valid @RequestBody NoteRequest request) {
        return ApiResponse.ok(learning.addNote(SecurityUtils.requireUser(), request));
    }

    @DeleteMapping("/me/notes/{id}")
    public ApiResponse<Void> deleteNote(@PathVariable String id) {
        learning.deleteNote(SecurityUtils.requireUser(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/courses/{courseId}/reviews")
    public ApiResponse<ReviewView> review(@PathVariable String courseId, @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(learning.review(SecurityUtils.requireUser(), courseId, request));
    }

    @PostMapping("/reports")
    public ApiResponse<String> report(@Valid @RequestBody ReportRequest request) {
        return ApiResponse.ok(admin.createReport(SecurityUtils.requireUser(), request).getId());
    }

    @GetMapping("/me/notifications")
    public ApiResponse<List<NotificationView>> notifications() {
        String userId = SecurityUtils.requireUser().id();
        return ApiResponse.ok(notifications.findByUserIdOrderByCreatedAtDesc(userId).stream().limit(30)
                .map(item -> new NotificationView(item.getId(), item.getType(), item.getTitle(), item.getBody(), item.getLink(), item.isRead(), item.getCreatedAt()))
                .toList());
    }

    @PostMapping("/me/notifications/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id) {
        Notification notification = notifications.findById(id).orElseThrow(() -> Errors.notFound("Notification not found."));
        if (!notification.getUserId().equals(SecurityUtils.requireUser().id())) {
            throw Errors.notFound("Notification not found.");
        }
        notification.setRead(true);
        notifications.save(notification);
        return ApiResponse.ok(null);
    }
}

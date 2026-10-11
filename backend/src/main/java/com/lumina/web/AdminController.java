package com.lumina.web;

import com.lumina.admin.AdminService;
import com.lumina.api.Requests.CategoryRequest;
import com.lumina.api.Requests.DisableRequest;
import com.lumina.api.Requests.RejectRequest;
import com.lumina.api.Requests.ResolveReportRequest;
import com.lumina.api.Requests.RoleRequest;
import com.lumina.api.Requests.StatusRequest;
import com.lumina.api.Views.AdminDashboard;
import com.lumina.api.Views.AuditView;
import com.lumina.api.Views.CategoryView;
import com.lumina.api.Views.CourseCard;
import com.lumina.api.Views.ReportView;
import com.lumina.api.Views.UserView;
import com.lumina.catalog.CatalogService;
import com.lumina.common.ApiResponse;
import com.lumina.common.Pages.PageData;
import com.lumina.domain.Enums.AccountStatus;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.ReportStatus;
import com.lumina.domain.Enums.Role;
import com.lumina.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
public class AdminController {
    private final AdminService admin;
    private final CatalogService catalog;

    public AdminController(AdminService admin, CatalogService catalog) {
        this.admin = admin;
        this.catalog = catalog;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboard> dashboard() {
        return ApiResponse.ok(admin.dashboard());
    }

    @GetMapping("/users")
    public ApiResponse<PageData<UserView>> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(admin.users(q, role, status, page, size));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserView> user(@PathVariable String id) {
        return ApiResponse.ok(admin.user(id));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<UserView> status(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(admin.setStatus(SecurityUtils.requireUser(), id, request.status()));
    }

    @PatchMapping("/users/{id}/role")
    public ApiResponse<UserView> role(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok(admin.setRole(SecurityUtils.requireUser(), id, request.role()));
    }

    @GetMapping("/courses")
    public ApiResponse<PageData<CourseCard>> courses(
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(admin.courses(status, q, page, size));
    }

    @PostMapping("/courses/{id}/approve")
    public ApiResponse<CourseCard> approve(@PathVariable String id) {
        return ApiResponse.ok(admin.approve(SecurityUtils.requireUser(), id));
    }

    @PostMapping("/courses/{id}/reject")
    public ApiResponse<CourseCard> reject(@PathVariable String id, @Valid @RequestBody RejectRequest request) {
        return ApiResponse.ok(admin.reject(SecurityUtils.requireUser(), id, request));
    }

    @PostMapping("/courses/{id}/archive")
    public ApiResponse<CourseCard> archive(@PathVariable String id) {
        return ApiResponse.ok(admin.archive(SecurityUtils.requireUser(), id));
    }

    @PostMapping("/courses/{id}/disable")
    public ApiResponse<CourseCard> disable(@PathVariable String id, @Valid @RequestBody DisableRequest request) {
        return ApiResponse.ok(admin.disable(SecurityUtils.requireUser(), id, request.disabled(), request.reason()));
    }

    @PostMapping("/courses/{id}/lessons/{lessonId}/remove")
    public ApiResponse<Void> removeLesson(@PathVariable String id, @PathVariable String lessonId, @RequestBody(required = false) RejectRequest request) {
        admin.removeLesson(SecurityUtils.requireUser(), id, lessonId, request == null ? null : request.reason());
        return ApiResponse.ok(null);
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryView>> categories() {
        return ApiResponse.ok(catalog.adminCategories());
    }

    @PostMapping("/categories")
    public ApiResponse<CategoryView> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(catalog.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<CategoryView> updateCategory(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(catalog.updateCategory(id, request));
    }

    @PostMapping("/categories/{id}/deactivate")
    public ApiResponse<CategoryView> deactivate(@PathVariable String id) {
        return ApiResponse.ok(catalog.deactivate(id));
    }

    @GetMapping("/reports")
    public ApiResponse<PageData<ReportView>> reports(@RequestParam(required = false) ReportStatus status, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(admin.reports(status, page, size));
    }

    @PostMapping("/reports/{id}/resolve")
    public ApiResponse<ReportView> resolve(@PathVariable String id, @Valid @RequestBody ResolveReportRequest request) {
        return ApiResponse.ok(admin.resolve(SecurityUtils.requireUser(), id, request));
    }

    @GetMapping("/audit")
    public ApiResponse<PageData<AuditView>> audit(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(admin.audit(page, size));
    }
}

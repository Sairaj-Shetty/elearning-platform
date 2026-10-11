package com.lumina.web;

import com.lumina.api.Views.CategoryView;
import com.lumina.api.Views.CourseCard;
import com.lumina.api.Views.CourseDetail;
import com.lumina.api.Views.InstructorPublic;
import com.lumina.api.Views.ReviewView;
import com.lumina.catalog.CatalogService;
import com.lumina.common.ApiResponse;
import com.lumina.common.Pages.PageData;
import com.lumina.learning.LearningService;
import com.lumina.security.SecurityUtils;
import com.lumina.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog")
public class CatalogController {
    private final CatalogService catalog;
    private final UserService users;
    private final LearningService learning;

    public CatalogController(CatalogService catalog, UserService users, LearningService learning) {
        this.catalog = catalog;
        this.users = users;
        this.learning = learning;
    }

    @GetMapping("/categories")
    @Operation(summary = "Active categories and subcategories")
    public ApiResponse<List<CategoryView>> categories() {
        return ApiResponse.ok(catalog.publicCategories());
    }

    @GetMapping("/courses")
    @Operation(summary = "Search published courses")
    public ApiResponse<PageData<CourseCard>> courses(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String language,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(catalog.search(q, domain, categoryId, difficulty, language, sort, page, size, SecurityUtils.currentUserOrNull()));
    }

    @GetMapping("/courses/{slug}")
    @Operation(summary = "Course detail. Drafts are hidden from students.")
    public ApiResponse<CourseDetail> course(@PathVariable String slug) {
        return ApiResponse.ok(catalog.bySlug(slug, SecurityUtils.currentUserOrNull()));
    }

    @GetMapping("/courses/{courseId}/reviews")
    public ApiResponse<List<ReviewView>> reviews(@PathVariable String courseId) {
        return ApiResponse.ok(learning.reviews(courseId));
    }

    @GetMapping("/instructors/{id}")
    public ApiResponse<InstructorPublic> instructor(@PathVariable String id) {
        return ApiResponse.ok(users.publicInstructor(id));
    }
}

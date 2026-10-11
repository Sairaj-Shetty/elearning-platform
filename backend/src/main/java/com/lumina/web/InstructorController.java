package com.lumina.web;

import com.lumina.api.Requests.CourseWriteRequest;
import com.lumina.api.Requests.LessonRequest;
import com.lumina.api.Requests.ReorderRequest;
import com.lumina.api.Requests.SectionRequest;
import com.lumina.api.Views.CourseCard;
import com.lumina.api.Views.CourseDetail;
import com.lumina.api.Views.InstructorDashboard;
import com.lumina.api.Views.LearnerRow;
import com.lumina.common.ApiResponse;
import com.lumina.course.CourseService;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.security.SecurityUtils;
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
@RequestMapping("/api/v1/instructor")
@Tag(name = "Instructor")
public class InstructorController {
    private final CourseService courses;

    public InstructorController(CourseService courses) {
        this.courses = courses;
    }

    @GetMapping("/dashboard")
    public ApiResponse<InstructorDashboard> dashboard() {
        return ApiResponse.ok(courses.dashboard(SecurityUtils.requireUser()));
    }

    @GetMapping("/courses")
    public ApiResponse<List<CourseCard>> mine(@RequestParam(required = false) CourseStatus status) {
        return ApiResponse.ok(courses.mine(SecurityUtils.requireUser(), status));
    }

    @PostMapping("/courses")
    public ApiResponse<CourseDetail> create(@Valid @RequestBody CourseWriteRequest request) {
        return ApiResponse.ok(courses.create(SecurityUtils.requireUser(), request));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<CourseDetail> one(@PathVariable String id) {
        return ApiResponse.ok(courses.mineOne(SecurityUtils.requireUser(), id));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<CourseDetail> update(@PathVariable String id, @Valid @RequestBody CourseWriteRequest request) {
        return ApiResponse.ok(courses.update(SecurityUtils.requireUser(), id, request));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        courses.deleteDraft(SecurityUtils.requireUser(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/courses/{id}/sections")
    public ApiResponse<CourseDetail> addSection(@PathVariable String id, @Valid @RequestBody SectionRequest request) {
        return ApiResponse.ok(courses.addSection(SecurityUtils.requireUser(), id, request));
    }

    @PutMapping("/courses/{id}/sections/{sectionId}")
    public ApiResponse<CourseDetail> updateSection(@PathVariable String id, @PathVariable String sectionId, @Valid @RequestBody SectionRequest request) {
        return ApiResponse.ok(courses.updateSection(SecurityUtils.requireUser(), id, sectionId, request));
    }

    @DeleteMapping("/courses/{id}/sections/{sectionId}")
    public ApiResponse<CourseDetail> deleteSection(@PathVariable String id, @PathVariable String sectionId) {
        return ApiResponse.ok(courses.deleteSection(SecurityUtils.requireUser(), id, sectionId));
    }

    @PutMapping("/courses/{id}/sections/reorder")
    public ApiResponse<CourseDetail> reorderSections(@PathVariable String id, @Valid @RequestBody ReorderRequest request) {
        return ApiResponse.ok(courses.reorderSections(SecurityUtils.requireUser(), id, request.orderedIds()));
    }

    @PostMapping("/courses/{id}/sections/{sectionId}/lessons")
    public ApiResponse<CourseDetail> addLesson(@PathVariable String id, @PathVariable String sectionId, @Valid @RequestBody LessonRequest request) {
        return ApiResponse.ok(courses.addLesson(SecurityUtils.requireUser(), id, sectionId, request));
    }

    @PutMapping("/courses/{id}/lessons/{lessonId}")
    public ApiResponse<CourseDetail> updateLesson(@PathVariable String id, @PathVariable String lessonId, @Valid @RequestBody LessonRequest request) {
        return ApiResponse.ok(courses.updateLesson(SecurityUtils.requireUser(), id, lessonId, request));
    }

    @DeleteMapping("/courses/{id}/lessons/{lessonId}")
    public ApiResponse<CourseDetail> deleteLesson(@PathVariable String id, @PathVariable String lessonId) {
        return ApiResponse.ok(courses.deleteLesson(SecurityUtils.requireUser(), id, lessonId));
    }

    @PutMapping("/courses/{id}/sections/{sectionId}/lessons/reorder")
    public ApiResponse<CourseDetail> reorderLessons(@PathVariable String id, @PathVariable String sectionId, @Valid @RequestBody ReorderRequest request) {
        return ApiResponse.ok(courses.reorderLessons(SecurityUtils.requireUser(), id, sectionId, request.orderedIds()));
    }

    @PostMapping("/courses/{id}/submit")
    public ApiResponse<CourseDetail> submit(@PathVariable String id) {
        return ApiResponse.ok(courses.submit(SecurityUtils.requireUser(), id));
    }

    @PostMapping("/courses/{id}/withdraw")
    public ApiResponse<CourseDetail> withdraw(@PathVariable String id) {
        return ApiResponse.ok(courses.withdraw(SecurityUtils.requireUser(), id));
    }

    @GetMapping("/courses/{id}/learners")
    public ApiResponse<List<LearnerRow>> learners(@PathVariable String id) {
        return ApiResponse.ok(courses.learners(SecurityUtils.requireUser(), id));
    }
}

package com.lumina.web;

import com.lumina.ai.AiService;
import com.lumina.api.Requests.AssistRequest;
import com.lumina.api.Requests.AssistantRequest;
import com.lumina.api.Requests.AttemptRequest;
import com.lumina.api.Requests.ModerateRequest;
import com.lumina.api.Requests.QuizGenerateRequest;
import com.lumina.api.Requests.RoadmapRequest;
import com.lumina.api.Views.AssistSuggestion;
import com.lumina.api.Views.AssistantReply;
import com.lumina.api.Views.AttemptView;
import com.lumina.api.Views.InsightsView;
import com.lumina.api.Views.ModerationResult;
import com.lumina.api.Views.QuizView;
import com.lumina.api.Views.RecommendationItem;
import com.lumina.api.Views.RoadmapView;
import com.lumina.common.ApiResponse;
import com.lumina.domain.AiSession;
import com.lumina.quiz.QuizService;
import com.lumina.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "AI")
public class AiController {
    private final AiService ai;
    private final QuizService quizzes;

    public AiController(AiService ai, QuizService quizzes) {
        this.ai = ai;
        this.quizzes = quizzes;
    }

    @PostMapping("/ai/assistant")
    public ApiResponse<AssistantReply> assistant(@Valid @RequestBody AssistantRequest request) {
        return ApiResponse.ok(ai.assistant(SecurityUtils.requireUser(), request));
    }

    @GetMapping("/ai/assistant/session")
    public ApiResponse<AiSession> session(@RequestParam(required = false) String courseId, @RequestParam(required = false) String lessonId) {
        return ApiResponse.ok(ai.session(SecurityUtils.requireUser(), courseId, lessonId));
    }

    @DeleteMapping("/ai/assistant/session")
    public ApiResponse<Void> clear(@RequestParam(required = false) String courseId, @RequestParam(required = false) String lessonId) {
        ai.clearSession(SecurityUtils.requireUser(), courseId, lessonId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/ai/recommendations")
    public ApiResponse<List<RecommendationItem>> recommendations() {
        return ApiResponse.ok(ai.recommendations(SecurityUtils.requireUser()));
    }

    @PostMapping("/ai/roadmaps")
    public ApiResponse<RoadmapView> roadmap(@Valid @RequestBody RoadmapRequest request) {
        return ApiResponse.ok(ai.createRoadmap(SecurityUtils.requireUser(), request));
    }

    @GetMapping("/ai/roadmaps")
    public ApiResponse<List<RoadmapView>> roadmaps() {
        return ApiResponse.ok(ai.myRoadmaps(SecurityUtils.requireUser()));
    }

    @PostMapping("/ai/roadmaps/{id}/stages/{index}/complete")
    public ApiResponse<RoadmapView> completeStage(@PathVariable String id, @PathVariable int index) {
        return ApiResponse.ok(ai.completeStage(SecurityUtils.requireUser(), id, index));
    }

    @GetMapping("/ai/insights")
    public ApiResponse<InsightsView> insights() {
        return ApiResponse.ok(ai.insights(SecurityUtils.requireUser()));
    }

    @PostMapping("/ai/quizzes/generate")
    public ApiResponse<QuizView> practiceQuiz(@Valid @RequestBody QuizGenerateRequest request) {
        return ApiResponse.ok(quizzes.generatePractice(SecurityUtils.requireUser(), request));
    }

    @GetMapping("/quizzes")
    public ApiResponse<List<QuizView>> quizzes(@RequestParam String courseId, @RequestParam(required = false) String lessonId) {
        return ApiResponse.ok(quizzes.list(SecurityUtils.requireUser(), courseId, lessonId));
    }

    @PostMapping("/quizzes/{id}/attempts")
    public ApiResponse<AttemptView> attempt(@PathVariable String id, @Valid @RequestBody AttemptRequest request) {
        return ApiResponse.ok(quizzes.attempt(SecurityUtils.requireUser(), id, request));
    }

    @GetMapping("/me/quiz-attempts")
    public ApiResponse<List<AttemptView>> attempts() {
        return ApiResponse.ok(quizzes.myAttempts(SecurityUtils.requireUser()));
    }

    @PostMapping("/instructor/ai/assist")
    public ApiResponse<AssistSuggestion> assist(@Valid @RequestBody AssistRequest request) {
        return ApiResponse.ok(ai.assist(SecurityUtils.requireUser(), request));
    }

    @PostMapping("/instructor/ai/quizzes")
    public ApiResponse<QuizView> instructorQuiz(@Valid @RequestBody QuizGenerateRequest request) {
        return ApiResponse.ok(quizzes.generateForInstructor(SecurityUtils.requireUser(), request));
    }

    @PostMapping("/instructor/quizzes/{id}/approve")
    public ApiResponse<QuizView> approve(@PathVariable String id) {
        return ApiResponse.ok(quizzes.approve(SecurityUtils.requireUser(), id));
    }

    @PostMapping("/admin/ai/review-text")
    public ApiResponse<ModerationResult> moderate(@Valid @RequestBody ModerateRequest request) {
        return ApiResponse.ok(ai.moderate(SecurityUtils.requireUser(), request));
    }

    @GetMapping("/ai/status")
    public ApiResponse<Map<String, String>> status() {
        return ApiResponse.ok(Map.of(
                "studyAssistant", "implemented",
                "recommendations", "implemented",
                "roadmap", "implemented",
                "quizzes", "implemented",
                "instructorAssist", "implemented",
                "moderation", "implemented",
                "insights", "implemented",
                "videoUnderstanding", "not-implemented"
        ));
    }
}

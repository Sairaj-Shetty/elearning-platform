package com.lumina.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.ai.GeminiClient;
import com.lumina.api.Requests.AnswerRequest;
import com.lumina.api.Requests.AttemptRequest;
import com.lumina.api.Requests.QuizGenerateRequest;
import com.lumina.api.Views.AnswerView;
import com.lumina.api.Views.AttemptView;
import com.lumina.api.Views.QuestionView;
import com.lumina.api.Views.QuizView;
import com.lumina.common.Errors;
import com.lumina.common.SimpleRateLimiter;
import com.lumina.course.CourseFinder;
import com.lumina.domain.Course;
import com.lumina.domain.Enums.QuestionType;
import com.lumina.domain.Enums.QuizSource;
import com.lumina.domain.Enums.QuizVisibility;
import com.lumina.domain.Enums.Role;
import com.lumina.domain.Quiz;
import com.lumina.domain.QuizAttempt;
import com.lumina.repo.CourseRepository;
import com.lumina.repo.EnrollmentRepository;
import com.lumina.repo.QuizAttemptRepository;
import com.lumina.repo.QuizRepository;
import com.lumina.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class QuizService {
    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final GeminiClient gemini;
    private final ObjectMapper mapper;
    private final SimpleRateLimiter limiter;

    public QuizService(QuizRepository quizzes, QuizAttemptRepository attempts, CourseRepository courses,
                       EnrollmentRepository enrollments, GeminiClient gemini, ObjectMapper mapper, SimpleRateLimiter limiter) {
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.courses = courses;
        this.enrollments = enrollments;
        this.gemini = gemini;
        this.mapper = mapper;
        this.limiter = limiter;
    }

    public QuizView generatePractice(AuthUser actor, QuizGenerateRequest request) {
        limiter.check("ai:" + actor.id() + ":quiz", 8, Duration.ofHours(1));
        if (enrollments.findByStudentIdAndCourseId(actor.id(), request.courseId()).isEmpty() && actor.role() == Role.STUDENT) {
            throw Errors.forbidden("Enroll before generating a practice quiz.");
        }
        Quiz quiz = build(actor.id(), request, QuizVisibility.PRACTICE, true);
        return toView(quiz, false);
    }

    public QuizView generateForInstructor(AuthUser actor, QuizGenerateRequest request) {
        limiter.check("ai:" + actor.id() + ":quiz", 8, Duration.ofHours(1));
        Course course = courses.findById(request.courseId()).orElseThrow(() -> Errors.notFound("Course not found."));
        if (!course.getInstructorId().equals(actor.id())) throw Errors.forbidden("You can generate quizzes only for your own course.");
        Quiz quiz = build(actor.id(), request, QuizVisibility.COURSE, false);
        return toView(quiz, true);
    }

    public QuizView approve(AuthUser actor, String quizId) {
        Quiz quiz = quizzes.findById(quizId).orElseThrow(() -> Errors.notFound("Quiz not found."));
        Course course = courses.findById(quiz.getCourseId()).orElseThrow(() -> Errors.notFound("Course not found."));
        if (!course.getInstructorId().equals(actor.id())) throw Errors.forbidden("You can approve only your own quiz.");
        quiz.setApproved(true);
        quizzes.save(quiz);
        return toView(quiz, true);
    }

    public List<QuizView> list(AuthUser actor, String courseId, String lessonId) {
        return quizzes.findByCourseIdAndLessonId(courseId, lessonId).stream()
                .filter(quiz -> visible(actor, quiz))
                .map(quiz -> toView(quiz, actor.role() != Role.STUDENT))
                .toList();
    }

    public AttemptView attempt(AuthUser actor, String quizId, AttemptRequest request) {
        Quiz quiz = quizzes.findById(quizId).orElseThrow(() -> Errors.notFound("Quiz not found."));
        if (!visible(actor, quiz)) throw Errors.forbidden("This quiz is not available.");
        QuizAttempt attempt = new QuizAttempt();
        attempt.setStudentId(actor.id());
        attempt.setQuizId(quiz.getId());
        attempt.setCourseId(quiz.getCourseId());
        attempt.setLessonId(quiz.getLessonId());
        attempt.setStartedAt(Instant.now());
        int total = 0;
        int max = 0;
        boolean approximate = false;
        for (Quiz.Question question : quiz.getQuestions()) {
            AnswerRequest answer = request.answers().stream()
                    .filter(item -> question.getQuestionId().equals(item.questionId()))
                    .findFirst().orElse(null);
            QuizAttempt.Answer scored = score(question, answer);
            attempt.getAnswers().add(scored);
            total += scored.getScore() == null ? 0 : scored.getScore();
            max += scored.getMaxScore();
            approximate = approximate || scored.isAiEvaluated();
        }
        attempt.setTotalScore(total);
        attempt.setMaxScore(max);
        attempt.setPercent(max == 0 ? 0 : Math.round(total * 1000.0 / max) / 10.0);
        attempt.setIncludesApproximateScore(approximate);
        attempt.setSubmittedAt(Instant.now());
        attempts.save(attempt);
        return toAttempt(attempt, quiz);
    }

    public List<AttemptView> myAttempts(AuthUser actor) {
        return attempts.findByStudentIdOrderBySubmittedAtDesc(actor.id()).stream()
                .map(attempt -> toAttempt(attempt, quizzes.findById(attempt.getQuizId()).orElse(null)))
                .toList();
    }

    private Quiz build(String ownerId, QuizGenerateRequest request, QuizVisibility visibility, boolean approved) {
        Course course = courses.findById(request.courseId()).orElseThrow(() -> Errors.notFound("Course not found."));
        String lessonTitle = request.lessonId() == null ? course.getTitle() : CourseFinder.lessonTitle(course, request.lessonId());
        String description = request.lessonId() == null ? course.getDescription() : CourseFinder.requireLesson(course, request.lessonId()).lesson().getDescription();
        int count = request.count() == null ? 4 : request.count();
        GeminiClient.Result result = gemini.generate(
                "Write study questions from the supplied lesson text only. Return JSON {\"title\":\"\",\"questions\":[{\"type\":\"MCQ\",\"prompt\":\"\",\"options\":[\"\",\"\",\"\",\"\"],\"correctIndex\":0,\"explanation\":\"\"}]}. Types may be MCQ, TRUE_FALSE, or SHORT. For SHORT, options can be empty and correctIndex null.",
                "Lesson: " + lessonTitle + "\n" + GeminiClient.trim(description, 3000) + "\nCount: " + count, true);
        Quiz quiz = new Quiz();
        quiz.setCourseId(course.getId());
        quiz.setLessonId(request.lessonId());
        quiz.setOwnerId(ownerId);
        quiz.setSource(QuizSource.AI);
        quiz.setVisibility(visibility);
        quiz.setApproved(approved);
        quiz.setAiGenerated(result.available());
        quiz.setCreatedAt(Instant.now());
        quiz.setTitle("Practice: " + lessonTitle);
        quiz.setQuestions(result.available() ? parse(result.text()) : fallbackQuestions(lessonTitle));
        if (quiz.getQuestions().isEmpty()) quiz.setQuestions(fallbackQuestions(lessonTitle));
        quizzes.save(quiz);
        return quiz;
    }

    private List<Quiz.Question> parse(String json) {
        List<Quiz.Question> questions = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(strip(json));
            for (JsonNode node : root.path("questions")) {
                Quiz.Question question = new Quiz.Question();
                question.setQuestionId(UUID.randomUUID().toString());
                String type = node.path("type").asText("MCQ");
                question.setType("TRUE_FALSE".equals(type) ? QuestionType.TRUE_FALSE : "SHORT".equals(type) ? QuestionType.SHORT : QuestionType.MCQ);
                question.setPrompt(node.path("prompt").asText(""));
                List<String> parsedOptions = new ArrayList<>();
                node.path("options").forEach(option -> parsedOptions.add(option.asText()));
                List<String> options = question.getType() == QuestionType.TRUE_FALSE && parsedOptions.size() < 2
                        ? List.of("True", "False") : parsedOptions;
                question.setOptions(options);
                question.setCorrectIndex(node.path("correctIndex").isNumber() ? Integer.valueOf(node.path("correctIndex").asInt()) : null);
                question.setExplanation(node.path("explanation").asText(""));
                if (question.getPrompt().isBlank()) continue;
                if (question.getType() != QuestionType.SHORT && (question.getCorrectIndex() == null || question.getCorrectIndex() < 0 || question.getCorrectIndex() >= question.getOptions().size())) {
                    continue;
                }
                questions.add(question);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return questions;
    }

    private List<Quiz.Question> fallbackQuestions(String lessonTitle) {
        Quiz.Question question = new Quiz.Question();
        question.setQuestionId(UUID.randomUUID().toString());
        question.setType(QuestionType.TRUE_FALSE);
        question.setPrompt("This question was created without Gemini. The lesson \"" + lessonTitle + "\" is part of the saved course outline.");
        question.setOptions(List.of("True", "False"));
        question.setCorrectIndex(0);
        question.setExplanation("Fallback question. It checks that you are looking at the saved lesson, not an AI-written concept.");
        return List.of(question);
    }

    private QuizAttempt.Answer score(Quiz.Question question, AnswerRequest given) {
        QuizAttempt.Answer answer = new QuizAttempt.Answer();
        answer.setQuestionId(question.getQuestionId());
        answer.setMaxScore(question.getPoints());
        if (given == null) {
            answer.setCorrect(false);
            answer.setScore(0);
            answer.setFeedback("No answer submitted.");
            return answer;
        }
        answer.setSelectedIndex(given.selectedIndex());
        answer.setTextAnswer(given.textAnswer());
        if (question.getType() == QuestionType.SHORT) {
            return scoreShort(question, answer);
        }
        boolean correct = given.selectedIndex() != null && given.selectedIndex().equals(question.getCorrectIndex());
        answer.setCorrect(correct);
        answer.setScore(correct ? question.getPoints() : 0);
        answer.setFeedback(question.getExplanation());
        return answer;
    }

    private QuizAttempt.Answer scoreShort(Quiz.Question question, QuizAttempt.Answer answer) {
        if (answer.getTextAnswer() == null || answer.getTextAnswer().isBlank()) {
            answer.setCorrect(false);
            answer.setScore(0);
            answer.setFeedback("No short answer was submitted.");
            return answer;
        }
        GeminiClient.Result result = gemini.generate(
                "Score a short student answer from 0 to 100. This is approximate. Return JSON {\"scorePercent\":0,\"feedback\":\"\"}.",
                "Question: " + question.getPrompt() + "\nExpected idea: " + question.getExplanation() + "\nAnswer: " + answer.getTextAnswer(),
                true);
        answer.setAiEvaluated(true);
        if (!result.available()) {
            answer.setScore(null);
            answer.setCorrect(null);
            answer.setFeedback("Saved, but not scored. " + result.message() + " Short-answer scoring is approximate when AI is available.");
            return answer;
        }
        try {
            JsonNode node = mapper.readTree(strip(result.text()));
            int percent = Math.max(0, Math.min(100, node.path("scorePercent").asInt(0)));
            answer.setScore(percent >= 60 ? question.getPoints() : 0);
            answer.setCorrect(percent >= 60);
            answer.setFeedback("Approximate AI evaluation (" + percent + "%). " + node.path("feedback").asText(""));
        } catch (Exception ex) {
            answer.setScore(null);
            answer.setFeedback("The approximate evaluation could not be read. Your answer was saved.");
        }
        return answer;
    }

    private boolean visible(AuthUser actor, Quiz quiz) {
        if (quiz.getVisibility() == QuizVisibility.PRACTICE) return quiz.getOwnerId().equals(actor.id());
        if (actor.role() == Role.ADMIN) return true;
        Course course = courses.findById(quiz.getCourseId()).orElse(null);
        if (course != null && course.getInstructorId().equals(actor.id())) return true;
        return quiz.isApproved() && enrollments.findByStudentIdAndCourseId(actor.id(), quiz.getCourseId()).isPresent();
    }

    private QuizView toView(Quiz quiz, boolean includeAnswers) {
        List<QuestionView> questions = quiz.getQuestions().stream()
                .map(question -> new QuestionView(question.getQuestionId(), question.getType().name(), question.getPrompt(), question.getOptions(), question.getPoints()))
                .toList();
        return new QuizView(quiz.getId(), quiz.getCourseId(), quiz.getLessonId(), quiz.getTitle(), quiz.getVisibility().name(),
                quiz.isApproved(), quiz.isAiGenerated(), questions, quiz.getCreatedAt());
    }

    private AttemptView toAttempt(QuizAttempt attempt, Quiz quiz) {
        List<AnswerView> answers = new ArrayList<>();
        for (QuizAttempt.Answer answer : attempt.getAnswers()) {
            String explanation = null;
            if (quiz != null) {
                explanation = quiz.getQuestions().stream().filter(question -> question.getQuestionId().equals(answer.getQuestionId()))
                        .map(Quiz.Question::getExplanation).findFirst().orElse(null);
            }
            answers.add(new AnswerView(answer.getQuestionId(), answer.getCorrect(), answer.getScore(), answer.getMaxScore(),
                    answer.isAiEvaluated(), answer.getFeedback(), explanation));
        }
        return new AttemptView(attempt.getId(), attempt.getQuizId(), attempt.getTotalScore(), attempt.getMaxScore(),
                attempt.getPercent(), attempt.isIncludesApproximateScore(), answers, attempt.getSubmittedAt());
    }

    private static String strip(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```json", "").replaceFirst("^```", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }
}

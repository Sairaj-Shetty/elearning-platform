package com.lumina.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.api.Requests.AssistRequest;
import com.lumina.api.Requests.AssistantRequest;
import com.lumina.api.Requests.ModerateRequest;
import com.lumina.api.Requests.RoadmapRequest;
import com.lumina.api.Views.AssistSuggestion;
import com.lumina.api.Views.AssistantReply;
import com.lumina.api.Views.CourseCard;
import com.lumina.api.Views.InsightItem;
import com.lumina.api.Views.InsightsView;
import com.lumina.api.Views.ModerationResult;
import com.lumina.api.Views.RecommendationItem;
import com.lumina.api.Views.RoadmapStageView;
import com.lumina.api.Views.RoadmapView;
import com.lumina.common.Errors;
import com.lumina.common.SimpleRateLimiter;
import com.lumina.course.CourseCardFactory;
import com.lumina.course.CourseFinder;
import com.lumina.domain.AiInteraction;
import com.lumina.domain.AiSession;
import com.lumina.domain.Course;
import com.lumina.domain.Enrollment;
import com.lumina.domain.Enums.AiFeature;
import com.lumina.domain.Enums.CourseStatus;
import com.lumina.domain.Enums.EnrollmentStatus;
import com.lumina.domain.Enums.ProgressStatus;
import com.lumina.domain.LessonProgress;
import com.lumina.domain.QuizAttempt;
import com.lumina.domain.Roadmap;
import com.lumina.domain.User;
import com.lumina.learning.RecommendationScorer;
import com.lumina.repo.AiInteractionRepository;
import com.lumina.repo.AiSessionRepository;
import com.lumina.repo.CourseRepository;
import com.lumina.repo.EnrollmentRepository;
import com.lumina.repo.LessonProgressRepository;
import com.lumina.repo.QuizAttemptRepository;
import com.lumina.repo.RoadmapRepository;
import com.lumina.repo.UserRepository;
import com.lumina.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiService {
    private final GeminiClient gemini;
    private final ObjectMapper mapper;
    private final SimpleRateLimiter limiter;
    private final CourseRepository courses;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;
    private final LessonProgressRepository progressRepo;
    private final QuizAttemptRepository attempts;
    private final RoadmapRepository roadmaps;
    private final AiSessionRepository sessions;
    private final AiInteractionRepository interactions;
    private final CourseCardFactory cards;
    private final RecommendationScorer scorer = new RecommendationScorer();

    public AiService(GeminiClient gemini, ObjectMapper mapper, SimpleRateLimiter limiter, CourseRepository courses,
                     UserRepository users, EnrollmentRepository enrollments, LessonProgressRepository progressRepo,
                     QuizAttemptRepository attempts, RoadmapRepository roadmaps, AiSessionRepository sessions,
                     AiInteractionRepository interactions, CourseCardFactory cards) {
        this.gemini = gemini;
        this.mapper = mapper;
        this.limiter = limiter;
        this.courses = courses;
        this.users = users;
        this.enrollments = enrollments;
        this.progressRepo = progressRepo;
        this.attempts = attempts;
        this.roadmaps = roadmaps;
        this.sessions = sessions;
        this.interactions = interactions;
        this.cards = cards;
    }

    public AssistantReply assistant(AuthUser actor, AssistantRequest request) {
        limiter.check("ai:" + actor.id() + ":assistant", 20, Duration.ofHours(1));
        Course course = request.courseId() == null ? null : courses.findById(request.courseId()).orElse(null);
        String context = course == null ? "No lesson was selected." : lessonContext(course, request.lessonId());
        String system = """
                You are Lumina's study assistant.
                Use only the course context for claims about this lesson.
                If the context does not contain the answer, say that and label any extra help as general knowledge, not course content.
                Never claim you watched or transcribed the video.
                Do not ask for passwords, tokens, or personal data.
                Keep the answer under 220 words.
                """;
        String user = "Course context:\n" + context + "\n\nStudent question:\n" + GeminiClient.trim(request.message(), 2000);
        GeminiClient.Result result = gemini.generate(system, user, false);
        String reply = result.available() ? result.text() : fallbackAssistant(request.message(), course, request.lessonId());
        String source = result.available() ? "GEMINI" : "FALLBACK";
        saveSession(actor.id(), request.courseId(), request.lessonId(), request.message(), reply, result.available());
        log(actor.id(), AiFeature.STUDY_ASSISTANT, request.courseId(), request.lessonId(), result.available() ? "SUCCESS" : result.code(), request.message());
        return new AssistantReply(result.available(), source, result.available() ? "Generated from the lesson text. The model has not watched the video." : result.message(), reply, null);
    }

    public AiSession session(AuthUser actor, String courseId, String lessonId) {
        return sessions.findByUserIdAndFeatureAndCourseIdAndLessonId(actor.id(), AiFeature.STUDY_ASSISTANT, courseId, lessonId).orElse(null);
    }

    public void clearSession(AuthUser actor, String courseId, String lessonId) {
        sessions.findByUserIdAndFeatureAndCourseIdAndLessonId(actor.id(), AiFeature.STUDY_ASSISTANT, courseId, lessonId)
                .ifPresent(sessions::delete);
    }

    public List<RecommendationItem> recommendations(AuthUser actor) {
        User student = users.findById(actor.id()).orElseThrow(() -> Errors.notFound("User not found."));
        if (student.getRecommendationCache() != null && student.getRecommendationCache().getGeneratedAt() != null
                && student.getRecommendationCache().getGeneratedAt().isAfter(Instant.now().minus(Duration.ofHours(24)))) {
            return fromCache(student);
        }
        List<Course> published = courses.findByStatus(CourseStatus.PUBLISHED).stream().filter(course -> !course.isContentDisabled()).toList();
        Set<String> enrolled = enrollments.findByStudentIdOrderByLastAccessedAtDesc(actor.id()).stream().map(Enrollment::getCourseId).collect(Collectors.toSet());
        boolean beginner = enrollments.findByStudentIdOrderByLastAccessedAtDesc(actor.id()).stream().anyMatch(item -> item.getProgressPercent() > 0);
        List<RecommendationScorer.Scored> scored = scorer.score(student, published, enrolled, beginner);
        boolean explain = gemini.configured();
        String explanationBlob = null;
        if (explain && !scored.isEmpty()) {
            limiter.check("ai:" + actor.id() + ":recommend", 8, Duration.ofHours(1));
            String prompt = scored.stream().map(item -> item.course().getId() + " | " + item.course().getTitle() + " | " + String.join("; ", item.reasons())).collect(Collectors.joining("\n"));
            GeminiClient.Result result = gemini.generate(
                    "Write one short sentence per course id explaining the recommendation. Use only the reasons given. Return JSON {\"items\":[{\"courseId\":\"\",\"explanation\":\"\"}]}.",
                    prompt, true);
            explanationBlob = result.available() ? result.text() : null;
            log(actor.id(), AiFeature.RECOMMENDATION, null, null, result.available() ? "SUCCESS" : result.code(), "recommendations");
        }
        List<RecommendationItem> items = new ArrayList<>();
        User.RecommendationCache cache = new User.RecommendationCache();
        cache.setGeneratedAt(Instant.now());
        for (RecommendationScorer.Scored item : scored) {
            String explanation = explanationFor(explanationBlob, item.course().getId());
            if (explanation == null) explanation = String.join(" ", item.reasons());
            String source = explanationBlob == null ? "RULES" : "RULES_WITH_GEMINI";
            items.add(new RecommendationItem(cards.card(item.course(), null), item.score(), item.reasons(), explanation, source));
            User.CachedItem cached = new User.CachedItem();
            cached.setCourseId(item.course().getId());
            cached.setScore(item.score());
            cached.setReasons(item.reasons());
            cached.setExplanation(explanation);
            cache.getItems().add(cached);
        }
        student.setRecommendationCache(cache);
        users.save(student);
        return items;
    }

    public RoadmapView createRoadmap(AuthUser actor, RoadmapRequest request) {
        limiter.check("ai:" + actor.id() + ":roadmap", 6, Duration.ofHours(1));
        List<Course> published = courses.findByStatus(CourseStatus.PUBLISHED).stream().filter(course -> !course.isContentDisabled()).toList();
        String catalog = published.stream().limit(40)
                .map(course -> course.getId() + " | " + course.getTitle() + " | " + course.getDifficulty() + " | " + String.join(",", course.getTags()))
                .collect(Collectors.joining("\n"));
        GeminiClient.Result result = gemini.generate(
                "Create a learning roadmap. Use only course ids from the catalog. Return JSON {\"stages\":[{\"title\":\"\",\"description\":\"\",\"concepts\":[],\"courseIds\":[],\"practice\":\"\",\"milestone\":\"\"}]}. 3 or 4 stages.",
                "Goal: " + request.goal() + "\nCatalog:\n" + catalog, true);
        Roadmap roadmap = new Roadmap();
        roadmap.setStudentId(actor.id());
        roadmap.setGoal(request.goal().trim());
        roadmap.setCreatedAt(Instant.now());
        roadmap.setUpdatedAt(roadmap.getCreatedAt());
        if (result.available()) {
            roadmap.setSource("GEMINI");
            roadmap.setModel(gemini.model());
            roadmap.setStages(parseStages(result.text(), published));
        }
        if (roadmap.getStages().isEmpty()) {
            roadmap.setSource("RULES");
            roadmap.setModel(null);
            roadmap.setStages(ruleStages(request.goal(), published));
        }
        roadmaps.save(roadmap);
        log(actor.id(), AiFeature.ROADMAP, null, null, result.available() ? "SUCCESS" : result.code(), request.goal());
        return toRoadmap(roadmap, published);
    }

    public List<RoadmapView> myRoadmaps(AuthUser actor) {
        List<Course> published = courses.findByStatus(CourseStatus.PUBLISHED);
        return roadmaps.findByStudentIdOrderByCreatedAtDesc(actor.id()).stream().map(roadmap -> toRoadmap(roadmap, published)).toList();
    }

    public RoadmapView completeStage(AuthUser actor, String id, int index) {
        Roadmap roadmap = roadmaps.findById(id).orElseThrow(() -> Errors.notFound("Roadmap not found."));
        if (!roadmap.getStudentId().equals(actor.id())) throw Errors.forbidden("You can update only your own roadmap.");
        if (index < 0 || index >= roadmap.getStages().size()) throw Errors.bad("VALIDATION_ERROR", "That stage does not exist.");
        roadmap.getStages().get(index).setCompleted(true);
        roadmap.getStages().get(index).setCompletedAt(Instant.now());
        roadmap.setUpdatedAt(Instant.now());
        roadmaps.save(roadmap);
        return toRoadmap(roadmap, courses.findByStatus(CourseStatus.PUBLISHED));
    }

    public InsightsView insights(AuthUser actor) {
        limiter.check("ai:" + actor.id() + ":insights", 10, Duration.ofHours(1));
        List<InsightItem> items = new ArrayList<>();
        List<Enrollment> mine = enrollments.findByStudentIdOrderByLastAccessedAtDesc(actor.id());
        for (Enrollment enrollment : mine) {
            if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) continue;
            Course course = courses.findById(enrollment.getCourseId()).orElse(null);
            if (course == null) continue;
            items.add(new InsightItem("INCOMPLETE", course.getTitle() + " is not finished",
                    enrollment.getCompletedLessonCount() + " of " + enrollment.getCountableLessonCount() + " countable lessons are complete.",
                    course.getId(), enrollment.getLastLessonId(), false));
            String next = nextLesson(course, actor.id());
            if (next != null) {
                items.add(new InsightItem("NEXT", "Suggested next lesson", CourseFinder.lessonTitle(course, next), course.getId(), next, false));
            }
        }
        attempts.findByStudentIdOrderBySubmittedAtDesc(actor.id()).stream()
                .filter(attempt -> attempt.getPercent() != null && attempt.getPercent() < 60)
                .limit(3)
                .forEach(attempt -> items.add(new InsightItem("QUIZ", "A practice quiz was below 60 percent",
                        "Score " + attempt.getPercent() + ". This is a hint to revise, not a final grade.",
                        attempt.getCourseId(), attempt.getLessonId(), true)));
        String narrative = items.isEmpty()
                ? "There is not enough saved progress yet to infer a learning gap. Enroll and watch a lesson first."
                : items.stream().limit(4).map(item -> item.title() + ": " + item.detail()).collect(Collectors.joining(" "));
        GeminiClient.Result result = gemini.generate(
                "Summarize these learning facts in 80 words. Do not invent scores. Label uncertain advice as a suggestion.",
                narrative, false);
        log(actor.id(), AiFeature.INSIGHTS, null, null, result.available() ? "SUCCESS" : result.code(), "insights");
        return new InsightsView(result.available(), result.available() ? result.text() : narrative, items.stream().limit(8).toList());
    }

    public AssistSuggestion assist(AuthUser actor, AssistRequest request) {
        limiter.check("ai:" + actor.id() + ":assist", 15, Duration.ofHours(1));
        String prompt = "Task: " + request.task() + "\nTitle: " + safe(request.title()) + "\nDescription: " + safe(request.description())
                + "\nDomain: " + safe(request.domain()) + "\nDifficulty: " + safe(request.difficulty())
                + "\nCategory: " + safe(request.categoryName()) + "\nExisting outline: " + safe(request.existingOutline());
        GeminiClient.Result result = gemini.generate(
                "Help an instructor draft course text. Do not claim the content is published. Return JSON {\"description\":\"\",\"objectives\":[],\"prerequisites\":[],\"sectionTitles\":[],\"tags\":[],\"lessonDescription\":\"\"}.",
                prompt, true);
        log(actor.id(), AiFeature.COURSE_ASSIST, null, null, result.available() ? "SUCCESS" : result.code(), request.task());
        if (!result.available()) {
            String title = safe(request.title()).isBlank() ? "this course" : request.title().trim();
            return new AssistSuggestion(false, "FALLBACK", result.message(),
                    "Students will learn the core ideas of " + title + ". Review this fallback before saving it. It was not written by Gemini.",
                    List.of("Explain the main idea of " + title, "Practice one small exercise", "Identify a common mistake"),
                    List.of("Comfort with the previous course in this path"),
                    List.of("Why this topic matters", "The core skill", "A short practice"),
                    List.of(title.toLowerCase(Locale.ROOT)),
                    "Describe what the learner should be able to do after this lesson.");
        }
        try {
            JsonNode node = mapper.readTree(strip(result.text()));
            return new AssistSuggestion(true, "GEMINI", "Review this draft. Nothing is saved until you apply it.",
                    node.path("description").asText(""),
                    textList(node.path("objectives")),
                    textList(node.path("prerequisites")),
                    textList(node.path("sectionTitles")),
                    textList(node.path("tags")),
                    node.path("lessonDescription").asText(""));
        } catch (Exception ex) {
            return new AssistSuggestion(false, "FALLBACK", "The AI response was not valid JSON. Nothing was saved.",
                    null, List.of(), List.of(), List.of(), List.of(), null);
        }
    }

    public ModerationResult moderate(AuthUser actor, ModerateRequest request) {
        limiter.check("ai:" + actor.id() + ":moderate", 20, Duration.ofHours(1));
        String text = "Title: " + safe(request.title()) + "\nDescription: " + safe(request.description()) + "\nExtra: " + safe(request.extraText());
        GeminiClient.Result result = gemini.generate(
                "You assist a human moderator. Do not decide to delete content. Review only the text. You have not watched any video. Return JSON {\"riskCategory\":\"NONE|SPAM|HARASSMENT|UNSAFE|OTHER\",\"severity\":\"LOW|MEDIUM|HIGH\",\"explanation\":\"\"}.",
                text, true);
        log(actor.id(), AiFeature.MODERATION, null, null, result.available() ? "SUCCESS" : result.code(), request.title());
        if (!result.available()) {
            return new ModerationResult(false, "MANUAL", "UNKNOWN", "UNKNOWN",
                    "AI review is unavailable. A person still needs to read the text. " + result.message(), true);
        }
        try {
            JsonNode node = mapper.readTree(strip(result.text()));
            return new ModerationResult(true, "GEMINI", node.path("riskCategory").asText("OTHER"),
                    node.path("severity").asText("LOW"), node.path("explanation").asText("No explanation returned."), true);
        } catch (Exception ex) {
            return new ModerationResult(false, "MANUAL", "UNKNOWN", "UNKNOWN", "The AI response could not be read. Review the text yourself.", true);
        }
    }

    private List<Roadmap.Stage> parseStages(String json, List<Course> published) {
        Set<String> allowed = published.stream().map(Course::getId).collect(Collectors.toSet());
        try {
            JsonNode root = mapper.readTree(strip(json));
            List<Roadmap.Stage> stages = new ArrayList<>();
            for (JsonNode node : root.path("stages")) {
                Roadmap.Stage stage = new Roadmap.Stage();
                stage.setTitle(node.path("title").asText("Stage"));
                stage.setDescription(node.path("description").asText(""));
                stage.setConcepts(textList(node.path("concepts")));
                stage.setCourseIds(textList(node.path("courseIds")).stream().filter(allowed::contains).toList());
                stage.setPractice(node.path("practice").asText(""));
                stage.setMilestone(node.path("milestone").asText(""));
                stages.add(stage);
            }
            return stages;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Roadmap.Stage> ruleStages(String goal, List<Course> published) {
        String needle = goal.toLowerCase(Locale.ROOT);
        List<Course> matched = published.stream()
                .filter(course -> needle.contains(course.getTitle().toLowerCase(Locale.ROOT))
                        || course.getTags().stream().anyMatch(tag -> needle.contains(tag.toLowerCase(Locale.ROOT)))
                        || course.getTitle().toLowerCase(Locale.ROOT).chars().anyMatch(ch -> false)
                        || sharesWord(needle, course))
                .sorted(Comparator.comparing(Course::getDifficulty))
                .limit(6)
                .toList();
        if (matched.isEmpty()) {
            matched = published.stream().limit(3).toList();
        }
        List<Roadmap.Stage> stages = new ArrayList<>();
        stages.add(stage("Foundations", "Start with the closest published beginner material.", matched.stream().limit(2).map(Course::getId).toList(), "Finish the free preview, then enroll."));
        stages.add(stage("Core practice", "Work through the main course and its lessons.", matched.stream().skip(2).limit(2).map(Course::getId).toList(), "Complete the countable video lessons."));
        stages.add(stage("Check your understanding", "Use a practice quiz and a short explanation in your own words.", List.of(), "Score a practice quiz and write one summary."));
        return stages;
    }

    private static Roadmap.Stage stage(String title, String description, List<String> courseIds, String practice) {
        Roadmap.Stage stage = new Roadmap.Stage();
        stage.setTitle(title);
        stage.setDescription(description);
        stage.setCourseIds(courseIds);
        stage.setPractice(practice);
        stage.setMilestone(title + " done");
        stage.setConcepts(List.of(title));
        return stage;
    }

    private static boolean sharesWord(String goal, Course course) {
        for (String word : goal.split("[^a-z0-9]+")) {
            if (word.length() < 4) continue;
            if (course.getTitle().toLowerCase(Locale.ROOT).contains(word)) return true;
            if (course.getTags() != null && course.getTags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(word))) return true;
        }
        return false;
    }

    private RoadmapView toRoadmap(Roadmap roadmap, List<Course> published) {
        List<RoadmapStageView> stages = new ArrayList<>();
        for (int i = 0; i < roadmap.getStages().size(); i++) {
            Roadmap.Stage stage = roadmap.getStages().get(i);
            List<CourseCard> linked = stage.getCourseIds().stream()
                    .map(id -> published.stream().filter(course -> course.getId().equals(id)).findFirst().orElse(null))
                    .filter(course -> course != null)
                    .map(course -> cards.card(course, null))
                    .toList();
            stages.add(new RoadmapStageView(i, stage.getTitle(), stage.getDescription(), stage.getConcepts(), linked, stage.getPractice(), stage.getMilestone(), stage.isCompleted()));
        }
        return new RoadmapView(roadmap.getId(), roadmap.getGoal(), roadmap.getSource(), roadmap.getModel(), stages, roadmap.getCreatedAt());
    }

    private String nextLesson(Course course, String studentId) {
        Set<String> done = progressRepo.findByStudentIdAndCourseId(studentId, course.getId()).stream()
                .filter(item -> item.getStatus() == ProgressStatus.COMPLETED)
                .map(LessonProgress::getLessonId)
                .collect(Collectors.toSet());
        for (Course.Section section : course.getSections()) {
            for (Course.Lesson lesson : section.getLessons()) {
                if (lesson.getStatus() == com.lumina.domain.Enums.LessonStatus.ACTIVE && !done.contains(lesson.getLessonId())) {
                    return lesson.getLessonId();
                }
            }
        }
        return null;
    }

    private String lessonContext(Course course, String lessonId) {
        StringBuilder builder = new StringBuilder();
        builder.append("Course: ").append(course.getTitle()).append('\n');
        builder.append("Objectives: ").append(String.join("; ", course.getLearningObjectives())).append('\n');
        if (lessonId != null) {
            CourseFinder.Located located = CourseFinder.requireLesson(course, lessonId);
            builder.append("Section: ").append(located.section().getTitle()).append('\n');
            builder.append("Lesson: ").append(located.lesson().getTitle()).append('\n');
            builder.append("Lesson description: ").append(located.lesson().getDescription()).append('\n');
        }
        builder.append("Other lesson titles: ");
        course.getSections().forEach(section -> section.getLessons().forEach(lesson -> builder.append(lesson.getTitle()).append("; ")));
        builder.append("\nThere is no transcript unless one is included above. Do not claim the video was watched.");
        return GeminiClient.trim(builder.toString(), 6000);
    }

    private String fallbackAssistant(String message, Course course, String lessonId) {
        String title = course == null ? "this topic" : (lessonId == null ? course.getTitle() : CourseFinder.lessonTitle(course, lessonId));
        return "AI is unavailable, so this is a study prompt rather than a generated explanation. Re-read the lesson \"" + title
                + "\" and answer your question in three sentences using only that description. Your question was: " + GeminiClient.trim(message, 240);
    }

    private void saveSession(String userId, String courseId, String lessonId, String question, String reply, boolean ai) {
        AiSession session = sessions.findByUserIdAndFeatureAndCourseIdAndLessonId(userId, AiFeature.STUDY_ASSISTANT, courseId, lessonId)
                .orElseGet(AiSession::new);
        session.setUserId(userId);
        session.setFeature(AiFeature.STUDY_ASSISTANT);
        session.setCourseId(courseId);
        session.setLessonId(lessonId);
        AiSession.Message user = new AiSession.Message();
        user.setRole("user");
        user.setContent(GeminiClient.trim(question, 2000));
        user.setCreatedAt(Instant.now());
        AiSession.Message assistant = new AiSession.Message();
        assistant.setRole("assistant");
        assistant.setContent(reply);
        assistant.setAiGenerated(ai);
        assistant.setCreatedAt(Instant.now());
        session.getMessages().add(user);
        session.getMessages().add(assistant);
        if (session.getMessages().size() > 20) {
            session.setMessages(new ArrayList<>(session.getMessages().subList(session.getMessages().size() - 20, session.getMessages().size())));
        }
        session.setUpdatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        sessions.save(session);
    }

    private void log(String userId, AiFeature feature, String courseId, String lessonId, String status, String summary) {
        AiInteraction interaction = new AiInteraction();
        interaction.setUserId(userId);
        interaction.setFeature(feature);
        interaction.setCourseId(courseId);
        interaction.setLessonId(lessonId);
        interaction.setStatus(status);
        interaction.setModel(gemini.configured() ? gemini.model() : "none");
        interaction.setPromptSummary(GeminiClient.trim(summary, 300));
        interaction.setCreatedAt(Instant.now());
        interaction.setExpiresAt(Instant.now().plus(Duration.ofDays(90)));
        interactions.save(interaction);
    }

    private List<RecommendationItem> fromCache(User student) {
        List<RecommendationItem> items = new ArrayList<>();
        for (User.CachedItem item : student.getRecommendationCache().getItems()) {
            Course course = courses.findById(item.getCourseId()).orElse(null);
            if (course == null || course.getStatus() != CourseStatus.PUBLISHED) continue;
            items.add(new RecommendationItem(cards.card(course, null), item.getScore(), item.getReasons(), item.getExplanation(), "CACHED"));
        }
        return items;
    }

    private String explanationFor(String json, String courseId) {
        if (json == null) return null;
        try {
            for (JsonNode node : mapper.readTree(strip(json)).path("items")) {
                if (courseId.equals(node.path("courseId").asText())) return node.path("explanation").asText(null);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (!item.asText().isBlank()) values.add(item.asText());
            });
        }
        return values;
    }

    private static String strip(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```json", "").replaceFirst("^```", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

package com.lumina;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.domain.Enums.AccountStatus;
import com.lumina.domain.Enums.Role;
import com.lumina.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class CoreFlowIT {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @org.springframework.test.context.DynamicPropertySource
    static void props(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("lumina.seed.enabled", () -> "false");
        registry.add("lumina.jwt.secret", () -> "test-secret-key-must-be-at-least-32-bytes");
        registry.add("lumina.media.root", () -> System.getProperty("java.io.tmpdir") + "/lumina-it-" + System.nanoTime());
        registry.add("lumina.gemini.api-key", () -> "");
    }

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;
    @Autowired MongoTemplate mongo;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void clean() {
        mongo.getDb().drop();
    }

    @Test
    void rolesEnrollmentProgressAndAiFallback() throws Exception {
        String adminToken = login(createAdmin());
        String instructorToken = token(post("/api/v1/auth/register", Map.of(
                "email", "instructor@test.local", "password", "password123", "fullName", "Instructor", "role", "INSTRUCTOR")));
        String studentToken = token(post("/api/v1/auth/register", Map.of(
                "email", "student@test.local", "password", "password123", "fullName", "Student", "role", "STUDENT")));

        ResponseEntity<String> forbidden = exchange("/api/v1/instructor/courses", HttpMethod.GET, studentToken, null);
        assertEquals(403, forbidden.getStatusCode().value());

        JsonNode category = data(exchange("/api/v1/admin/categories", HttpMethod.POST, adminToken, Map.of(
                "name", "Programming", "description", "Code", "domain", "TECHNICAL")));
        java.util.Map<String, Object> courseBody = new java.util.HashMap<>();
        courseBody.put("title", "Test Java");
        courseBody.put("shortDescription", "A short course");
        courseBody.put("description", "A detailed description for the test course.");
        courseBody.put("categoryId", category.get("id").asText());
        courseBody.put("domain", "TECHNICAL");
        courseBody.put("difficulty", "BEGINNER");
        courseBody.put("language", "English");
        courseBody.put("learningObjectives", java.util.List.of("Read a test"));
        courseBody.put("prerequisites", java.util.List.of());
        courseBody.put("targetAudience", java.util.List.of("Students"));
        courseBody.put("tags", java.util.List.of("java"));
        courseBody.put("skills", java.util.List.of("Java"));
        JsonNode course = data(exchange("/api/v1/instructor/courses", HttpMethod.POST, instructorToken, courseBody));
        String courseId = course.get("id").asText();
        JsonNode withSection = data(exchange("/api/v1/instructor/courses/" + courseId + "/sections", HttpMethod.POST, instructorToken,
                Map.of("title", "Start", "description", "First section")));
        String sectionId = withSection.get("sections").get(0).get("sectionId").asText();
        JsonNode withLesson = data(exchange("/api/v1/instructor/courses/" + courseId + "/sections/" + sectionId + "/lessons",
                HttpMethod.POST, instructorToken, Map.of(
                        "title", "What the test covers",
                        "description", "A lesson description used by the assistant.",
                        "objectives", java.util.List.of("See the rule"),
                        "estimatedDurationMinutes", 8,
                        "access", "ENROLLED_ONLY",
                        "resources", java.util.List.of()
                )));
        String lessonId = withLesson.get("sections").get(0).get("lessons").get(0).get("lessonId").asText();

        JsonNode catalogBefore = data(exchange("/api/v1/courses", HttpMethod.GET, null, null));
        assertEquals(0, catalogBefore.get("total").asInt());

        exchange("/api/v1/instructor/courses/" + courseId + "/submit", HttpMethod.POST, instructorToken, null);
        exchange("/api/v1/admin/courses/" + courseId + "/approve", HttpMethod.POST, adminToken, null);
        JsonNode catalogAfter = data(exchange("/api/v1/courses?q=Test", HttpMethod.GET, null, null));
        assertEquals(1, catalogAfter.get("total").asInt());

        data(exchange("/api/v1/enrollments", HttpMethod.POST, studentToken, Map.of("courseId", courseId)));
        ResponseEntity<String> duplicate = exchange("/api/v1/enrollments", HttpMethod.POST, studentToken, Map.of("courseId", courseId));
        assertEquals(409, duplicate.getStatusCode().value());
        assertTrue(duplicate.getBody().contains("ALREADY_ENROLLED"));

        uploadSample(instructorToken, courseId, lessonId);
        JsonNode jumped = data(exchange("/api/v1/me/progress", HttpMethod.PUT, studentToken, Map.of(
                "courseId", courseId, "lessonId", lessonId, "positionSeconds", 100, "durationSeconds", 100)));
        assertFalse(jumped.get("completed").asBoolean());

        JsonNode ai = data(exchange("/api/v1/ai/assistant", HttpMethod.POST, studentToken, Map.of(
                "courseId", courseId, "lessonId", lessonId, "message", "What should I review?")));
        assertFalse(ai.get("aiAvailable").asBoolean());
        assertTrue(ai.get("reply").asText().length() > 10);
    }

    private String createAdmin() {
        User admin = new User();
        admin.setEmail("admin@test.local");
        admin.setPasswordHash(encoder.encode("password123"));
        admin.setFullName("Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setCreatedAt(Instant.now());
        mongo.save(admin);
        return "admin@test.local";
    }

    private String login(String email) throws Exception {
        return token(post("/api/v1/auth/login", Map.of("email", email, "password", "password123")));
    }

    private ResponseEntity<String> post(String path, Object body) {
        return exchange(path, HttpMethod.POST, null, body);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private void uploadSample(String token, String courseId, String lessonId) {
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ClassPathResource("sample/sample-lesson.mp4"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/instructor/courses/" + courseId + "/lessons/" + lessonId + "/video",
                HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
        assertEquals(200, response.getStatusCode().value(), response.getBody());
    }

    private String token(ResponseEntity<String> response) throws Exception {
        assertEquals(200, response.getStatusCode().value(), response.getBody());
        return mapper.readTree(response.getBody()).path("data").path("accessToken").asText();
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
        return mapper.readTree(response.getBody()).path("data");
    }
}

package com.lumina.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final RestClient client;

    public GeminiClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(props.gemini().timeoutSeconds(), 5)));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return props.gemini().apiKey() != null && !props.gemini().apiKey().isBlank();
    }

    public String model() {
        return props.gemini().model();
    }

    public Result generate(String system, String user, boolean json) {
        if (!configured()) {
            return Result.unavailable("NOT_CONFIGURED", "Gemini is not configured. Add GEMINI_API_KEY to the environment.");
        }
        String model = props.gemini().model() == null || props.gemini().model().isBlank()
                ? "gemini-3.5-flash-lite" : props.gemini().model();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", trim(system, 4000));
        body.putArray("contents").addObject().put("role", "user").putArray("parts").addObject().put("text", trim(user, 12000));
        ObjectNode config = body.putObject("generationConfig");
        config.put("temperature", json ? 0.2 : 0.5);
        config.put("maxOutputTokens", 2048);
        if (json) config.put("responseMimeType", "application/json");
        try {
            String response = client.post()
                    .uri(url)
                    .header("x-goog-api-key", props.gemini().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            JsonNode root = mapper.readTree(response);
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                return Result.unavailable("EMPTY", "The AI service returned an empty answer.");
            }
            return new Result(true, text, null, null);
        } catch (HttpStatusCodeException ex) {
            log.warn("Gemini HTTP {}: {}", ex.getStatusCode().value(), ex.getStatusText());
            if (ex.getStatusCode().value() == 429) {
                return Result.unavailable("RATE_LIMITED", "The free Gemini quota is exhausted right now. Try again later.");
            }
            if (ex.getStatusCode().value() == 404) {
                return Result.unavailable("MODEL_NOT_FOUND", "The configured Gemini model was not found. Check GEMINI_MODEL.");
            }
            return Result.unavailable("UPSTREAM", "The AI service rejected the request. The rest of Lumina still works.");
        } catch (ResourceAccessException ex) {
            log.warn("Gemini timeout: {}", ex.getMessage());
            return Result.unavailable("TIMEOUT", "The AI service timed out. Try again in a moment.");
        } catch (Exception ex) {
            log.warn("Gemini failure: {}", ex.getMessage());
            return Result.unavailable("UPSTREAM", "The AI service is unavailable. The rest of Lumina still works.");
        }
    }

    public static String trim(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replaceAll("(?i)bearer\\s+[a-z0-9\\-._~+/]+=*", "[redacted]");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    public record Result(boolean available, String text, String code, String message) {
        static Result unavailable(String code, String message) {
            return new Result(false, null, code, message);
        }
    }
}

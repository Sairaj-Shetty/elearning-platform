package com.lumina.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "lumina")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Media media,
        Seed seed,
        Gemini gemini,
        Cookie cookie
) {
    public record Jwt(String secret, int accessMinutes, int refreshDays) {}

    public record Cors(String origins) {
        public List<String> list() {
            if (origins == null || origins.isBlank()) {
                return List.of();
            }
            return Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Media(String root, long maxVideoBytes, long maxImageBytes) {}

    public record Seed(boolean enabled, String password) {}

    public record Gemini(String apiKey, String model, int timeoutSeconds) {}

    public record Cookie(boolean secure) {}
}

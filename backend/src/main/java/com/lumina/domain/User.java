package com.lumina.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lumina.domain.Enums.AccountStatus;
import com.lumina.domain.Enums.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Document("users")
public class User {
    @Id
    private String id;
    @Indexed(unique = true)
    private String email;
    @JsonIgnore
    private String passwordHash;
    private Role role;
    private AccountStatus status = AccountStatus.ACTIVE;
    private String fullName;
    private String avatarKey;
    private String bio;
    private String headline;
    private List<String> interests = new ArrayList<>();
    private List<String> skills = new ArrayList<>();
    private List<String> learningGoals = new ArrayList<>();
    private List<String> preferredCategoryIds = new ArrayList<>();
    private List<String> qualifications = new ArrayList<>();
    private String experience;
    private List<PortfolioLink> portfolioLinks = new ArrayList<>();
    private RecommendationCache recommendationCache;
    private boolean seedData;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PortfolioLink {
        private String label;
        private String url;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RecommendationCache {
        private Instant generatedAt;
        private List<CachedItem> items = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CachedItem {
        private String courseId;
        private int score;
        private List<String> reasons = new ArrayList<>();
        private String explanation;
    }
}

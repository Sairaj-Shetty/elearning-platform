package com.lumina.domain;

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
@Document("learningRoadmaps")
public class Roadmap {
    @Id
    private String id;
    @Indexed
    private String studentId;
    private String goal;
    private String source;
    private String model;
    private List<Stage> stages = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Stage {
        private String title;
        private String description;
        private List<String> concepts = new ArrayList<>();
        private List<String> courseIds = new ArrayList<>();
        private String practice;
        private String milestone;
        private boolean completed;
        private Instant completedAt;
    }
}

package com.lumina.domain;

import com.lumina.domain.Enums.AiFeature;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Document("aiInteractions")
public class AiInteraction {
    @Id
    private String id;
    
    private String userId;
    private AiFeature feature;
    private String courseId;
    private String lessonId;
    private String status;
    private String model;
    private String promptSummary;
    private Instant createdAt;
    @Indexed
    private Instant expiresAt;
}

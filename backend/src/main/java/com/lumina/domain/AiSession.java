package com.lumina.domain;

import com.lumina.domain.Enums.AiFeature;
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
@Document("aiSessions")
public class AiSession {
    @Id
    private String id;
    
    private String userId;
    private AiFeature feature;
    private String courseId;
    private String lessonId;
    private List<Message> messages = new ArrayList<>();
    private Instant updatedAt;
    @Indexed
    private Instant expiresAt;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private Instant createdAt;
        private boolean aiGenerated;
    }
}

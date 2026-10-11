package com.lumina.domain;

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
@Document("notifications")
public class Notification {
    @Id
    private String id;
    @Indexed
    private String userId;
    private String type;
    private String title;
    private String body;
    private String link;
    private boolean read;
    private Instant createdAt;
}

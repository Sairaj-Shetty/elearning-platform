package com.lumina.domain;

import com.lumina.domain.Enums.NoteType;
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
@Document("notes")
public class Note {
    @Id
    private String id;
    @Indexed
    private String studentId;
    private String courseId;
    private String lessonId;
    private NoteType type = NoteType.NOTE;
    private String body;
    private Double positionSeconds;
    private Instant createdAt;
    private Instant updatedAt;
}

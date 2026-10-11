package com.lumina.domain;

import com.lumina.domain.Enums.EventType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@Document("learningEvents")
@CompoundIndex(name = "student_time", def = "{'studentId': 1, 'occurredAt': -1}")
public class LearningEvent {
    @Id
    private String id;
    private String studentId;
    private String courseId;
    private String lessonId;
    private EventType eventType;
    private Instant occurredAt;
    private Map<String, Object> payload = new HashMap<>();
    private boolean seedData;
}

package com.lumina.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Document("reviews")
@CompoundIndex(name = "student_course_review_uidx", def = "{'studentId': 1, 'courseId': 1}", unique = true)
public class Review {
    @Id
    private String id;
    private String studentId;
    private String studentName;
    private String courseId;
    private int rating;
    private String comment;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean seedData;
}

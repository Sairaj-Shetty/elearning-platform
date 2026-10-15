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
@Document("certificates")
@CompoundIndex(name = "student_course_cert_uidx", def = "{'studentId': 1, 'courseId': 1}", unique = true)
public class Certificate {
    @Id
    private String id;
    private String studentId;
    private String courseId;
    private String studentName;
    private String courseTitle;
    private Instant completedAt;
    private Instant issuedAt;
    private boolean seedData;
}

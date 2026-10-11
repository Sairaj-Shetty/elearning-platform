package com.lumina.domain;

import com.lumina.domain.Enums.EnrollmentStatus;
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
@Document("enrollments")
@CompoundIndex(name = "student_course_uidx", def = "{'studentId': 1, 'courseId': 1}", unique = true)
public class Enrollment {
    @Id
    private String id;
    private String studentId;
    private String courseId;
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;
    private int curriculumVersionAtEnrollment;
    private int completedLessonCount;
    private int countableLessonCount;
    private double progressPercent;
    private Instant enrolledAt;
    private Instant lastAccessedAt;
    private String lastLessonId;
    private Double lastPositionSeconds;
    private Instant completedAt;
    private boolean seedData;
}

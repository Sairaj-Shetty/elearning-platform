package com.lumina.domain;

import com.lumina.domain.Enums.ProgressStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Document("lessonProgress")
@CompoundIndex(name = "student_lesson_uidx", def = "{'studentId': 1, 'lessonId': 1}", unique = true)
public class LessonProgress {
    @Id
    private String id;
    @Indexed
    private String studentId;
    @Indexed
    private String courseId;
    private String lessonId;
    private String sectionId;
    private ProgressStatus status = ProgressStatus.IN_PROGRESS;
    private double lastPositionSeconds;
    private double durationSeconds;
    private double accumulatedWatchSeconds;
    private double maxWatchedPercent;
    private Instant firstOpenedAt;
    private Instant lastWatchedAt;
    private Instant completedAt;
    private String completionMethod;
    private boolean openedEventWritten;
    private int highestMilestone;
    private boolean seedData;
}

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
@Document("quizAttempts")
public class QuizAttempt {
    @Id
    private String id;
    @Indexed
    private String studentId;
    private String quizId;
    private String courseId;
    private String lessonId;
    private Instant startedAt;
    private Instant submittedAt;
    private List<Answer> answers = new ArrayList<>();
    private int totalScore;
    private int maxScore;
    private Double percent;
    private boolean includesApproximateScore;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Answer {
        private String questionId;
        private Integer selectedIndex;
        private String textAnswer;
        private Boolean correct;
        private Integer score;
        private Integer maxScore;
        private boolean aiEvaluated;
        private String feedback;
    }
}

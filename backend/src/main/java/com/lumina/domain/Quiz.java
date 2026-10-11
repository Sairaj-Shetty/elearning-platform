package com.lumina.domain;

import com.lumina.domain.Enums.QuestionType;
import com.lumina.domain.Enums.QuizSource;
import com.lumina.domain.Enums.QuizVisibility;
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
@Document("quizzes")
public class Quiz {
    @Id
    private String id;
    @Indexed
    private String courseId;
    private String lessonId;
    private String ownerId;
    private String title;
    private QuizSource source = QuizSource.AI;
    private QuizVisibility visibility = QuizVisibility.PRACTICE;
    private boolean approved;
    private boolean aiGenerated = true;
    private List<Question> questions = new ArrayList<>();
    private Instant createdAt;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Question {
        private String questionId;
        private QuestionType type;
        private String prompt;
        private List<String> options = new ArrayList<>();
        private Integer correctIndex;
        private String explanation;
        private int points = 1;
    }
}

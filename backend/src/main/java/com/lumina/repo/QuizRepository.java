package com.lumina.repo;

import com.lumina.domain.Quiz;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface QuizRepository extends MongoRepository<Quiz, String> {
    List<Quiz> findByCourseIdAndLessonId(String courseId, String lessonId);
}

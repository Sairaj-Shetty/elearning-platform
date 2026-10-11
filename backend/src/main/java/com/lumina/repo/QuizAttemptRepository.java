package com.lumina.repo;

import com.lumina.domain.QuizAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface QuizAttemptRepository extends MongoRepository<QuizAttempt, String> {
    List<QuizAttempt> findByStudentIdOrderBySubmittedAtDesc(String studentId);
    List<QuizAttempt> findByStudentIdAndCourseId(String studentId, String courseId);
}

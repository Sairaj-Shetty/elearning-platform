package com.lumina.repo;

import com.lumina.domain.Enums.ProgressStatus;
import com.lumina.domain.LessonProgress;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends MongoRepository<LessonProgress, String> {
    Optional<LessonProgress> findByStudentIdAndLessonId(String studentId, String lessonId);
    List<LessonProgress> findByStudentIdAndCourseId(String studentId, String courseId);
    long countByStudentIdAndCourseIdAndStatus(String studentId, String courseId, ProgressStatus status);
}

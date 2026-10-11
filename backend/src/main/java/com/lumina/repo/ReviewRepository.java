package com.lumina.repo;

import com.lumina.domain.Review;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {
    Optional<Review> findByStudentIdAndCourseId(String studentId, String courseId);
    List<Review> findByCourseIdOrderByCreatedAtDesc(String courseId);
}

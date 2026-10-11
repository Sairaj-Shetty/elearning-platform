package com.lumina.repo;

import com.lumina.domain.Course;
import com.lumina.domain.Enums.CourseStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends MongoRepository<Course, String> {
    Optional<Course> findBySlug(String slug);
    List<Course> findByInstructorId(String instructorId);
    List<Course> findByStatus(CourseStatus status);
    long countByStatus(CourseStatus status);
    long countByInstructorId(String instructorId);
    long countByCategoryIdOrSubcategoryId(String categoryId, String subcategoryId);
}

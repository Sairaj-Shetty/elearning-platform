package com.lumina.repo;

import com.lumina.domain.Enrollment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends MongoRepository<Enrollment, String> {
    Optional<Enrollment> findByStudentIdAndCourseId(String studentId, String courseId);
    List<Enrollment> findByStudentIdOrderByLastAccessedAtDesc(String studentId);
    List<Enrollment> findByCourseId(String courseId);
    long countByCourseId(String courseId);
    long countByStudentId(String studentId);
}

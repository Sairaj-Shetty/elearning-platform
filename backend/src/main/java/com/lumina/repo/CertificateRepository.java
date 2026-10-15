package com.lumina.repo;

import com.lumina.domain.Certificate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends MongoRepository<Certificate, String> {
    List<Certificate> findByStudentIdOrderByIssuedAtDesc(String studentId);
    Optional<Certificate> findByStudentIdAndCourseId(String studentId, String courseId);
}

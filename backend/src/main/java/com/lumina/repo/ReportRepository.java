package com.lumina.repo;

import com.lumina.domain.Enums.ReportStatus;
import com.lumina.domain.Report;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReportRepository extends MongoRepository<Report, String> {
    long countByStatus(ReportStatus status);
}

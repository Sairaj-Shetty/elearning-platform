package com.lumina.repo;

import com.lumina.domain.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    boolean existsByActionAndTargetId(String action, String targetId);
}

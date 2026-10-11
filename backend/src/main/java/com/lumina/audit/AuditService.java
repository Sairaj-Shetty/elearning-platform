package com.lumina.audit;

import com.lumina.domain.AuditLog;
import com.lumina.domain.Enums.Role;
import com.lumina.repo.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuditService {
    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    public void log(String actorId, Role role, String action, String targetType, String targetId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setActorRole(role);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetails(details);
        entry.setCreatedAt(Instant.now());
        logs.save(entry);
    }
}

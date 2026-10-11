package com.lumina.domain;

import com.lumina.domain.Enums.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Document("auditLogs")
public class AuditLog {
    @Id
    private String id;
    @Indexed
    private String actorId;
    private Role actorRole;
    private String action;
    private String targetType;
    private String targetId;
    private String details;
    private Instant createdAt;
    private boolean seedData;
}

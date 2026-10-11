package com.lumina.domain;

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
@Document("refreshTokens")
public class RefreshToken {
    @Id
    private String id;
    @Indexed
    private String userId;
    @Indexed(unique = true)
    private String tokenHash;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt;
    private String userAgent;
}

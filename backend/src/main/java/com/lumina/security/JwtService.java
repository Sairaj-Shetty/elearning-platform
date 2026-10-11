package com.lumina.security;

import com.lumina.config.AppProperties;
import com.lumina.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {
    private final AppProperties props;
    private final SecretKey key;

    public JwtService(AppProperties props) {
        if (props.jwt().secret() == null || props.jwt().secret().length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters.");
        }
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String accessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId())
                .claim("role", user.getRole().name())
                .claim("purpose", "access")
                .claim("name", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.jwt().accessMinutes(), ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public String mediaTicket(String userId, String courseId, String lessonId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId == null ? "anonymous" : userId)
                .claim("purpose", "media")
                .claim("cid", courseId)
                .claim("lid", lessonId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(2, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public int accessSeconds() {
        return props.jwt().accessMinutes() * 60;
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}

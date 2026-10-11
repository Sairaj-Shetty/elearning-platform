package com.lumina.auth;

import com.lumina.api.Requests.LoginRequest;
import com.lumina.api.Requests.RegisterRequest;
import com.lumina.api.Views.AuthView;
import com.lumina.api.Views.DemoAccount;
import com.lumina.common.ApiException;
import com.lumina.common.Errors;
import com.lumina.common.SimpleRateLimiter;
import com.lumina.common.Tokens;
import com.lumina.config.AppProperties;
import com.lumina.domain.Enums.AccountStatus;
import com.lumina.domain.Enums.Role;
import com.lumina.domain.RefreshToken;
import com.lumina.domain.User;
import com.lumina.repo.RefreshTokenRepository;
import com.lumina.repo.UserRepository;
import com.lumina.security.JwtService;
import com.lumina.user.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {
    public static final String REFRESH_COOKIE = "lumina_refresh";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AppProperties props;
    private final SimpleRateLimiter limiter;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, AppProperties props, SimpleRateLimiter limiter) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.limiter = limiter;
    }

    public AuthSession register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw Errors.forbidden("Admin accounts cannot be created from the public registration form.");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setStatus(AccountStatus.ACTIVE);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(user.getCreatedAt());
        try {
            users.save(user);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_IN_USE", "An account with this email already exists.");
        }
        return issue(user, null);
    }

    public AuthSession login(LoginRequest request, String clientKey) {
        limiter.check("login:" + clientKey, 8, Duration.ofMinutes(10));
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Email or password is incorrect.");
        }
        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account is suspended. Contact an administrator.");
        }
        user.setLastLoginAt(Instant.now());
        users.save(user);
        return issue(user, null);
    }

    public AuthSession refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Login is required.");
        }
        RefreshToken stored = refreshTokens.findByTokenHash(Tokens.sha256(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Your session expired. Log in again."));
        if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Your session expired. Log in again.");
        }
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Your session expired. Log in again."));
        if (user.getStatus() == AccountStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account is suspended.");
        }
        stored.setRevokedAt(Instant.now());
        refreshTokens.save(stored);
        return issue(user, stored.getUserAgent());
    }

    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(Tokens.sha256(rawToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokens.save(token);
        });
    }

    public List<DemoAccount> demoAccounts() {
        if (!props.seed().enabled()) {
            throw Errors.notFound("Demo accounts are not available.");
        }
        String password = props.seed().password();
        String note = "Development sample account. Do not enable seeding in production.";
        return List.of(
                new DemoAccount("ADMIN", "admin@lumina.local", password, "Lumina Admin", note),
                new DemoAccount("INSTRUCTOR", "meera@lumina.local", password, "Meera Iyer", note),
                new DemoAccount("INSTRUCTOR", "arjun@lumina.local", password, "Arjun Rao", note),
                new DemoAccount("STUDENT", "arya@lumina.local", password, "Arya Shah", note),
                new DemoAccount("STUDENT", "rohan@lumina.local", password, "Rohan Das", note)
        );
    }

    public int refreshSeconds() {
        return props.jwt().refreshDays() * 24 * 60 * 60;
    }

    public boolean cookieSecure() {
        return props.cookie().secure();
    }

    private AuthSession issue(User user, String userAgent) {
        String raw = Tokens.random();
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(Tokens.sha256(raw));
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(token.getCreatedAt().plus(Duration.ofDays(props.jwt().refreshDays())));
        token.setUserAgent(userAgent);
        refreshTokens.save(token);
        AuthView view = new AuthView(jwt.accessToken(user), jwt.accessSeconds(), UserMapper.toView(user));
        return new AuthSession(view, raw);
    }

    public record AuthSession(AuthView view, String refreshToken) {}
}

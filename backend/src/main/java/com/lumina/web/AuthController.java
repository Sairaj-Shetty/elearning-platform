package com.lumina.web;

import com.lumina.api.Requests.LoginRequest;
import com.lumina.api.Requests.RegisterRequest;
import com.lumina.api.Views.AuthView;
import com.lumina.api.Views.DemoAccount;
import com.lumina.api.Views.UserView;
import com.lumina.auth.AuthService;
import com.lumina.common.ApiResponse;
import com.lumina.security.SecurityUtils;
import com.lumina.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {
    private final AuthService auth;
    private final UserService users;

    public AuthController(AuthService auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a student or instructor")
    public ApiResponse<AuthView> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthService.AuthSession session = auth.register(request);
        cookie(response, session.refreshToken(), auth.refreshSeconds());
        return ApiResponse.ok(session.view());
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive an access token plus a refresh cookie")
    public ApiResponse<AuthView> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        String key = http.getRemoteAddr() == null ? "unknown" : http.getRemoteAddr();
        AuthService.AuthSession session = auth.login(request, key);
        cookie(response, session.refreshToken(), auth.refreshSeconds());
        return ApiResponse.ok(session.view());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token and issue a new access token")
    public ApiResponse<AuthView> refresh(@CookieValue(name = AuthService.REFRESH_COOKIE, required = false) String raw, HttpServletResponse response) {
        AuthService.AuthSession session = auth.refresh(raw);
        cookie(response, session.refreshToken(), auth.refreshSeconds());
        return ApiResponse.ok(session.view());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token")
    public ApiResponse<Void> logout(@CookieValue(name = AuthService.REFRESH_COOKIE, required = false) String raw, HttpServletResponse response) {
        auth.logout(raw);
        cookie(response, "", 0);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user profile")
    public ApiResponse<UserView> me() {
        return ApiResponse.ok(users.me(SecurityUtils.requireUser()));
    }

    @GetMapping("/demo-accounts")
    @Operation(summary = "Development sample accounts. Returns 404 when seeding is disabled.")
    public ApiResponse<List<DemoAccount>> demo() {
        return ApiResponse.ok(auth.demoAccounts());
    }

    private void cookie(HttpServletResponse response, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(AuthService.REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(auth.cookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

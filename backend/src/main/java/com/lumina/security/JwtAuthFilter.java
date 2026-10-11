package com.lumina.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.common.ApiResponse;
import com.lumina.domain.Enums.AccountStatus;
import com.lumina.domain.User;
import com.lumina.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public JwtAuthFilter(JwtService jwtService, UserRepository users, ObjectMapper mapper) {
        this.jwtService = jwtService;
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                var claims = jwtService.parse(token);
                String purpose = claims.get("purpose", String.class);
                if (!"access".equals(purpose)) {
                    chain.doFilter(request, response);
                    return;
                }
                Optional<User> found = users.findById(claims.getSubject());
                if (found.isEmpty()) {
                    chain.doFilter(request, response);
                    return;
                }
                User user = found.get();
                if (user.getStatus() == AccountStatus.SUSPENDED) {
                    write(response, 403, "ACCOUNT_SUSPENDED", "This account is suspended.");
                    return;
                }
                AuthUser principal = AuthUser.from(user);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), ApiResponse.fail(code, message, java.util.List.of()));
    }
}

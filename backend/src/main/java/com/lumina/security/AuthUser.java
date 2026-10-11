package com.lumina.security;

import com.lumina.domain.Enums.Role;
import com.lumina.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AuthUser(String id, String email, Role role, String fullName) implements UserDetails {
    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getRole(), user.getFullName());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return ""; }

    @Override
    public String getUsername() { return email; }
}

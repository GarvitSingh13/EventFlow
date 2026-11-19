package com.eventflow.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class SecurityUser implements UserDetails {

    private final UUID userId;
    private final UUID organizationId;
    private final String email;
    private final String role;

    public SecurityUser(UUID userId, UUID organizationId, String email, String role) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.email = email;
        this.role = role;
    }

    public UUID getUserId() { return userId; }
    public UUID getOrganizationId() { return organizationId; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() { return null; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}

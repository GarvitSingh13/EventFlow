package com.eventflow.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final UUID organizationId;
    private final String keyName;

    public ApiKeyAuthentication(UUID organizationId, String keyName) {
        super(List.of(new SimpleGrantedAuthority("ROLE_API_KEY"), new SimpleGrantedAuthority("ROLE_PRODUCER")));
        this.organizationId = organizationId;
        this.keyName = keyName;
        setAuthenticated(true);
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return organizationId;
    }

    @Override
    public String getName() {
        return keyName;
    }
}

package com.eventflow.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public class SecurityUtils {

    public static UUID getCurrentOrganizationId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        if (auth.getPrincipal() instanceof SecurityUser user) {
            return user.getOrganizationId();
        } else if (auth instanceof ApiKeyAuthentication apiKeyAuth) {
            return apiKeyAuth.getOrganizationId();
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unrecognized authentication principal");
    }
}

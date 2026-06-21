package com.example.dacn2_beserver.security;

import java.security.Principal;

public record AuthPrincipal(String userId, String sessionId) implements Principal {
    @Override
    public String getName() {
        return userId;
    }
}
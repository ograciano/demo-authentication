package com.vass.authentication.api.dto;

public record RegisterResponse(
        Long userId,
        String name,
        String lastName,
        String email,
        String role,
        boolean active
) {
}

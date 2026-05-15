package com.vass.authentication.api.dto;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        Long userId,
        String username
) {
}

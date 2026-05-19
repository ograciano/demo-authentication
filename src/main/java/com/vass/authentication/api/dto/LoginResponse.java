package com.vass.authentication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de login")
public record LoginResponse(
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "3600") long expiresIn,
        UserResponse user
) {
}

package com.vass.authentication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de renovación de tokens")
public record RefreshResponse(
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") String accessToken,
        @Schema(example = "3600") long expiresIn,
        @Schema(example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") String refreshToken,
        @Schema(example = "86400") long refreshExpiresIn
) {
}

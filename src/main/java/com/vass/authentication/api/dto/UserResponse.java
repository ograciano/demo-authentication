package com.vass.authentication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Información básica del usuario autenticado")
public record UserResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "oscar.demo@email.com") String email,
        @Schema(example = "Oscar Demo") String name
) {
}

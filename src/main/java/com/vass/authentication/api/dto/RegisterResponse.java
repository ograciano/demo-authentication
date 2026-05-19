package com.vass.authentication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Respuesta de registro")
public record RegisterResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "Oscar Demo") String name,
        @Schema(example = "oscar.demo@email.com") String email,
        @Schema(example = "ACTIVE") String status,
        List<String> roles
) {
}

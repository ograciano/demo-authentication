package com.vass.authentication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload de login")
public record LoginRequest(
        @NotBlank @Email @Schema(example = "oscar.demo@email.com") String email,
        @NotBlank @Schema(example = "Password123!") String password
) {
}

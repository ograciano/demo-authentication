package com.vass.authentication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Payload de registro")
public record RegisterRequest(
        @NotBlank @Schema(example = "Oscar Demo") String name,
        @NotBlank @Email @Schema(example = "oscar.demo@email.com") String email,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$",
                message = "must have at least 8 characters, one uppercase, one lowercase, one number, and one special character"
        )
        @Schema(example = "Password123!") String password
) {
}

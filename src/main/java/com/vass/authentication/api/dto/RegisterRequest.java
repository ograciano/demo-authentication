package com.vass.authentication.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "name is required")
        @Size(max = 80, message = "name max length is 80")
        String name,
        @NotBlank(message = "lastName is required")
        @Size(max = 80, message = "lastName max length is 80")
        String lastName,
        @NotBlank(message = "email is required")
        @Email(message = "email format is invalid")
        String email,
        @NotBlank(message = "password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$",
                message = "password must be at least 8 chars and include upper, lower, number and special char"
        )
        String password,
        @NotBlank(message = "passwordConfirm is required")
        String passwordConfirm
) {
    @AssertTrue(message = "password and passwordConfirm must match")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirm);
    }
}

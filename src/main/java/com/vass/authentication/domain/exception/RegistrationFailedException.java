package com.vass.authentication.domain.exception;

public class RegistrationFailedException extends RuntimeException {
    public RegistrationFailedException(Throwable cause) {
        super("Registration failed", cause);
    }
}

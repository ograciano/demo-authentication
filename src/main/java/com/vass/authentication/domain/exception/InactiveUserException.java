package com.vass.authentication.domain.exception;

public class InactiveUserException extends RuntimeException {

    public InactiveUserException(String message) {
        super(message);
    }
}

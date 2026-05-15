package com.vass.authentication.domain.exception;

import org.springframework.http.HttpStatus;

public class PermissionAssignmentException extends RuntimeException {

    private final HttpStatus status;
    private final String reason;

    public PermissionAssignmentException(HttpStatus status, String reason, Throwable cause) {
        super("Permission assignment failed", cause);
        this.status = status;
        this.reason = reason;
    }

    public PermissionAssignmentException(HttpStatus status, String reason) {
        super("Permission assignment failed");
        this.status = status;
        this.reason = reason;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}

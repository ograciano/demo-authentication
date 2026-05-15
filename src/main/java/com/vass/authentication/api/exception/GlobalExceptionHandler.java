package com.vass.authentication.api.exception;

import com.vass.authentication.api.dto.ErrorResponse;
import com.vass.authentication.domain.exception.EmailAlreadyExistsException;
import com.vass.authentication.domain.exception.InvalidCredentialsException;
import com.vass.authentication.domain.exception.PermissionAssignmentException;
import com.vass.authentication.domain.exception.RegistrationFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "Bad Request",
                "Validation failed",
                Instant.now(),
                details
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "Bad Request",
                "Invalid request payload",
                Instant.now(),
                Map.of()
        ));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
                "Unauthorized",
                "Invalid credentials",
                Instant.now(),
                Map.of()
        ));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                "Conflict",
                "Email already exists",
                Instant.now(),
                Map.of()
        ));
    }

    @ExceptionHandler(PermissionAssignmentException.class)
    public ResponseEntity<ErrorResponse> handlePermissionAssignment(PermissionAssignmentException ex) {
        String error = switch (ex.getStatus()) {
            case BAD_REQUEST -> "Bad Request";
            case NOT_FOUND -> "Not Found";
            case SERVICE_UNAVAILABLE -> "Service Unavailable";
            default -> "Internal Server Error";
        };
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(
                error,
                "Permission assignment failed",
                Instant.now(),
                Map.of()
        ));
    }

    @ExceptionHandler(RegistrationFailedException.class)
    public ResponseEntity<ErrorResponse> handleRegistrationFailed(RegistrationFailedException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                "Internal Server Error",
                "Registration failed",
                Instant.now(),
                Map.of()
        ));
    }
}

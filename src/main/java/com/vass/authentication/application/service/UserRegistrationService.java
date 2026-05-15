package com.vass.authentication.application.service;

import com.vass.authentication.api.dto.RegisterRequest;
import com.vass.authentication.api.dto.RegisterResponse;
import com.vass.authentication.domain.exception.EmailAlreadyExistsException;
import com.vass.authentication.domain.exception.PermissionAssignmentException;
import com.vass.authentication.domain.exception.RegistrationFailedException;
import com.vass.authentication.domain.model.User;
import com.vass.authentication.infrastructure.client.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.client.dto.AssignPermissionRequest;
import com.vass.authentication.infrastructure.client.dto.AssignPermissionResponse;
import com.vass.authentication.infrastructure.repository.UserRepository;
import feign.FeignException;
import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserRegistrationService.class);
    private static final String INITIAL_ROLE = "REPORT_READER";
    private static final String STATUS_ASSIGNED = "ASSIGNED";
    private static final String STATUS_ALREADY_ASSIGNED = "ALREADY_ASSIGNED";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationServiceClient authorizationServiceClient;
    private final String initialPermission;

    public UserRegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthorizationServiceClient authorizationServiceClient,
            @Value("${app.registration.initial-permission:REPORT:READ}") String initialPermission
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorizationServiceClient = authorizationServiceClient;
        this.initialPermission = initialPermission;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException();
        }

        User user = new User(
                null,
                normalizedEmail,
                request.name().trim(),
                request.name().trim(),
                request.lastName().trim(),
                INITIAL_ROLE,
                passwordEncoder.encode(request.password()),
                true
        );

        User persisted = null;
        try {
            persisted = userRepository.save(user);
            AssignPermissionResponse permissionResponse = authorizationServiceClient.assignPermission(
                    persisted.getId(),
                    new AssignPermissionRequest(initialPermission)
            );
            validateAssignmentStatus(permissionResponse, persisted.getId());
            LOGGER.info("Initial permission assignment succeeded userId={} status={}",
                    persisted.getId(), permissionResponse.status());
            LOGGER.info("User registered successfully userId={}", persisted.getId());
            return new RegisterResponse(
                    persisted.getId(),
                    persisted.getFirstName(),
                    persisted.getLastName(),
                    persisted.getEmail(),
                    persisted.getRole(),
                    persisted.isActive()
            );
        } catch (FeignException ex) {
            HttpStatus status = resolveStatus(ex);
            LOGGER.warn("Registration rollback due to permission assignment failure userId={} userEmail={} status={}",
                    persisted != null ? persisted.getId() : null, normalizedEmail, status.value());
            throw new PermissionAssignmentException(status, ex.getClass().getSimpleName(), ex);
        } catch (RuntimeException ex) {
            LOGGER.error("Registration failed unexpectedly userId={} userEmail={}",
                    persisted != null ? persisted.getId() : null, normalizedEmail);
            throw new RegistrationFailedException(ex);
        }
    }

    private void validateAssignmentStatus(AssignPermissionResponse response, Long userId) {
        if (response == null || response.status() == null) {
            throw new PermissionAssignmentException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing assignment response");
        }
        if (!STATUS_ASSIGNED.equals(response.status()) && !STATUS_ALREADY_ASSIGNED.equals(response.status())) {
            LOGGER.warn("Unexpected permission assignment status userId={} status={}", userId, response.status());
            throw new PermissionAssignmentException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected assignment status");
        }
    }

    private HttpStatus resolveStatus(FeignException ex) {
        if (ex instanceof RetryableException || ex.status() < 0 || ex.status() >= 500) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (ex.status() == 400) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex.status() == 404) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

package com.vass.authentication.application.service;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vass.authentication.api.dto.LoginRequest;
import com.vass.authentication.api.dto.LoginResponse;
import com.vass.authentication.api.dto.RegisterRequest;
import com.vass.authentication.api.dto.RegisterResponse;
import com.vass.authentication.api.dto.UserResponse;
import com.vass.authentication.domain.exception.DuplicateEmailException;
import com.vass.authentication.domain.exception.InactiveUserException;
import com.vass.authentication.domain.exception.InvalidCredentialsException;
import com.vass.authentication.domain.exception.PermissionBootstrapException;
import com.vass.authentication.domain.exception.PermissionsServiceException;
import com.vass.authentication.infrastructure.integration.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;
import com.vass.authentication.infrastructure.security.JwtService;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales inválidas";
    private static final String DUPLICATED_EMAIL_MESSAGE = "El correo ya se encuentra registrado";
    private static final String DEFAULT_ROLE = "VIEWER";
    private static final String DEFAULT_PERMISSION = "REPORT:READ";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthorizationServiceClient authorizationServiceClient;
    private final LoginAttemptService loginAttemptService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthorizationServiceClient authorizationServiceClient,
                       LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authorizationServiceClient = authorizationServiceClient;
        this.loginAttemptService = loginAttemptService;
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        loginAttemptService.checkRateLimit(clientIp, request.email());
        loginAttemptService.checkLockout(request.email());

        UserEntity user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(request.email());
                    log.info("event=LOGIN_ATTEMPT result=FAILURE reason=INVALID_CREDENTIALS path=/api/auth/login");
                    return new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
                });

        if (!user.isActive()) {
            log.info("event=LOGIN_ATTEMPT result=FAILURE reason=INACTIVE_USER path=/api/auth/login");
            throw new InactiveUserException("Usuario inactivo o bloqueado");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(request.email());
            log.info("event=LOGIN_ATTEMPT result=FAILURE reason=INVALID_CREDENTIALS path=/api/auth/login");
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        loginAttemptService.resetFailures(request.email());
        List<String> permissions = normalizePermissions(authorizationServiceClient.getPermissionsForUser(user.getId()));

        String token = jwtService.generateToken(user.getEmail(), permissions);
        log.info("event=LOGIN_ATTEMPT result=SUCCESS path=/api/auth/login");
        return new LoginResponse(
                "Bearer",
                token,
                jwtService.getExpirationSeconds(),
                new UserResponse(user.getId(), user.getEmail(), user.getName())
        );
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateEmailException(DUPLICATED_EMAIL_MESSAGE);
        }

        UserEntity candidate = UserEntity.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(true)
                .role(DEFAULT_ROLE)
                .build();

        UserEntity savedUser;
        try {
            savedUser = userRepository.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException(DUPLICATED_EMAIL_MESSAGE);
        }

        try {
            authorizationServiceClient.assignInitialPermission(savedUser.getId(), DEFAULT_PERMISSION);
        } catch (PermissionsServiceException ex) {
            throw new PermissionBootstrapException("No se pudo asignar permiso inicial", ex);
        }

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                "ACTIVE",
                List.of(savedUser.getRole())
        );
    }

    private List<String> normalizePermissions(List<String> sourcePermissions) {
        if (sourcePermissions == null || sourcePermissions.isEmpty()) {
            return List.of();
        }
        return sourcePermissions.stream()
                .filter(permission -> permission != null && !permission.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}

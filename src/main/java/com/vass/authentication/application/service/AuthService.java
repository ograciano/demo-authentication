package com.vass.authentication.application.service;

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
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales inválidas";
    private static final String DUPLICATED_EMAIL_MESSAGE = "El correo ya se encuentra registrado";
    private static final String DEFAULT_ROLE = "VIEWER";
    private static final String DEFAULT_PERMISSION = "REPORT:READ";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthorizationServiceClient authorizationServiceClient;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthorizationServiceClient authorizationServiceClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authorizationServiceClient = authorizationServiceClient;
    }

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!user.isActive()) {
            throw new InactiveUserException("Usuario inactivo o bloqueado");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        List<String> permissions = normalizePermissions(authorizationServiceClient.getPermissionsForUser(user.getId()));

        String token = jwtService.generateToken(user.getEmail(), permissions);
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

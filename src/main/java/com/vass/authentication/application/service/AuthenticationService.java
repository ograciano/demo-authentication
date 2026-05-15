package com.vass.authentication.application.service;

import com.vass.authentication.api.dto.LoginRequest;
import com.vass.authentication.api.dto.LoginResponse;
import com.vass.authentication.domain.exception.InvalidCredentialsException;
import com.vass.authentication.domain.model.User;
import com.vass.authentication.infrastructure.repository.UserRepository;
import com.vass.authentication.infrastructure.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final JwtService jwtService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PermissionService permissionService,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionService = permissionService;
        this.jwtService = jwtService;
    }

    public LoginResponse authenticate(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        List<String> permissions = permissionService.getUserPermissions(user.getId());
        String token = jwtService.generateToken(user.getId(), user.getEmail(), permissions);

        return new LoginResponse(
                "Bearer",
                token,
                jwtService.getExpiresInSeconds(),
                user.getId(),
                user.getUsername()
        );
    }
}

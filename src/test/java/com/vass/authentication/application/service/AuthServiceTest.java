package com.vass.authentication.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vass.authentication.api.dto.LoginRequest;
import com.vass.authentication.api.dto.LoginResponse;
import com.vass.authentication.api.dto.RegisterRequest;
import com.vass.authentication.api.dto.RegisterResponse;
import com.vass.authentication.domain.exception.DuplicateEmailException;
import com.vass.authentication.domain.exception.InactiveUserException;
import com.vass.authentication.domain.exception.InvalidCredentialsException;
import com.vass.authentication.domain.exception.PermissionBootstrapException;
import com.vass.authentication.domain.exception.PermissionsServiceException;
import com.vass.authentication.infrastructure.integration.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;
import com.vass.authentication.infrastructure.security.JwtService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthorizationServiceClient authorizationServiceClient;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, authorizationServiceClient);
    }

    @Test
    void testLogin_UserNotFound_ReturnsUnauthorized() {
        when(userRepository.findByEmailIgnoreCase("missing@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@email.com", "Password123!")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void testLogin_UserInactive_ReturnsForbidden() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("inactive@email.com")
                .passwordHash("hash")
                .name("Inactive")
                .active(false)
                .role("VIEWER")
                .build();
        when(userRepository.findByEmailIgnoreCase("inactive@email.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("inactive@email.com", "Password123!")))
                .isInstanceOf(InactiveUserException.class);
    }

    @Test
    void testLogin_InvalidPassword_ReturnsUnauthorized() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("user@email.com")
                .passwordHash("hash")
                .name("User")
                .active(true)
                .role("VIEWER")
                .build();
        when(userRepository.findByEmailIgnoreCase("user@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@email.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void testLogin_PermissionsFallbackEmptyList_ReturnsToken() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("user@email.com")
                .passwordHash("hash")
                .name("User")
                .active(true)
                .role("VIEWER")
                .build();
        when(userRepository.findByEmailIgnoreCase("user@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(authorizationServiceClient.getPermissionsForUser(1L)).thenReturn(List.of());
        when(jwtService.generateToken(eq("user@email.com"), eq(List.of()))).thenReturn("token-fallback");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("user@email.com", "Password123!"));

        verify(jwtService).generateToken("user@email.com", List.of());
        assertThat(response.accessToken()).isEqualTo("token-fallback");
    }

    @Test
    void testLogin_ValidCredentials_ReturnsTokenWithNormalizedPermissions() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("user@email.com")
                .passwordHash("hash")
                .name("User")
                .active(true)
                .role("VIEWER")
                .build();
        when(userRepository.findByEmailIgnoreCase("user@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hash")).thenReturn(true);
        when(authorizationServiceClient.getPermissionsForUser(1L))
                .thenReturn(List.of("REPORT:READ", "REPORT:DOWNLOAD", "REPORT:READ", " "));
        when(jwtService.generateToken(eq("user@email.com"), any())).thenReturn("token-value");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("user@email.com", "Password123!"));

        verify(jwtService).generateToken("user@email.com", List.of("REPORT:DOWNLOAD", "REPORT:READ"));
        assertThat(response.accessToken()).isEqualTo("token-value");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("user@email.com");
    }

    @Test
    void testRegister_DuplicateEmail_ThrowsConflict() {
        when(userRepository.existsByEmailIgnoreCase("existing@email.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("User", "existing@email.com", "Password123!")))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("El correo ya se encuentra registrado");
    }

    @Test
    void testRegister_PermissionBootstrapFailure_ThrowsAndRollsBack() {
        RegisterRequest request = new RegisterRequest("New User", "new@email.com", "Password123!");
        UserEntity savedUser = UserEntity.builder()
                .id(10L)
                .name("New User")
                .email("new@email.com")
                .passwordHash("encoded-password")
                .active(true)
                .role("VIEWER")
                .build();

        when(userRepository.existsByEmailIgnoreCase("new@email.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenReturn(savedUser);
        doThrow(new PermissionsServiceException("down", new RuntimeException("down")))
                .when(authorizationServiceClient).assignInitialPermission(10L, "REPORT:READ");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(PermissionBootstrapException.class)
                .hasMessage("No se pudo asignar permiso inicial");
    }

    @Test
    void testRegister_ValidData_ReturnsCreatedResponse() {
        RegisterRequest request = new RegisterRequest("New User", "new@email.com", "Password123!");
        UserEntity savedUser = UserEntity.builder()
                .id(10L)
                .name("New User")
                .email("new@email.com")
                .passwordHash("encoded-password")
                .active(true)
                .role("VIEWER")
                .build();

        when(userRepository.existsByEmailIgnoreCase("new@email.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenReturn(savedUser);
        doNothing().when(authorizationServiceClient).assignInitialPermission(10L, "REPORT:READ");

        RegisterResponse response = authService.register(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.roles()).containsExactly("VIEWER");
        assertThat(response.email()).isEqualTo("new@email.com");
    }
}

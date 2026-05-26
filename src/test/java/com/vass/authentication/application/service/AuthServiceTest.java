package com.vass.authentication.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vass.authentication.api.dto.LoginRequest;
import com.vass.authentication.api.dto.LoginResponse;
import com.vass.authentication.api.dto.RegisterRequest;
import com.vass.authentication.api.dto.RegisterResponse;
import com.vass.authentication.domain.exception.AccountLockedException;
import com.vass.authentication.domain.exception.DuplicateEmailException;
import com.vass.authentication.domain.exception.InactiveUserException;
import com.vass.authentication.domain.exception.InvalidCredentialsException;
import com.vass.authentication.domain.exception.PermissionBootstrapException;
import com.vass.authentication.domain.exception.PermissionsServiceException;
import com.vass.authentication.domain.exception.RateLimitExceededException;
import com.vass.authentication.infrastructure.integration.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;
import com.vass.authentication.infrastructure.security.JwtService;

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

    @Mock
    private LoginAttemptService loginAttemptService;

    private AuthService authService;

    private static final String CLIENT_IP = "127.0.0.1";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService,
                authorizationServiceClient, loginAttemptService);
    }

    @Test
    void testLogin_UserNotFound_ReturnsUnauthorized() {
        when(userRepository.findByEmailIgnoreCase("missing@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@email.com", "Password123!"), CLIENT_IP))
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

        assertThatThrownBy(() -> authService.login(new LoginRequest("inactive@email.com", "Password123!"), CLIENT_IP))
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

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@email.com", "wrong"), CLIENT_IP))
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

        LoginResponse response = authService.login(new LoginRequest("user@email.com", "Password123!"), CLIENT_IP);

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

        LoginResponse response = authService.login(new LoginRequest("user@email.com", "Password123!"), CLIENT_IP);

        verify(jwtService).generateToken("user@email.com", List.of("REPORT:DOWNLOAD", "REPORT:READ"));
        assertThat(response.accessToken()).isEqualTo("token-value");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("user@email.com");
    }

    @Test
    void testLogin_RateLimitExceeded_ThrowsRateLimitExceeded() {
        doThrow(new RateLimitExceededException("Demasiadas solicitudes, intente más tarde"))
                .when(loginAttemptService).checkRateLimit(anyString(), anyString());

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@email.com", "Password123!"), CLIENT_IP))
                .isInstanceOf(RateLimitExceededException.class);

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void testLogin_AccountLocked_ThrowsAccountLocked() {
        doThrow(new AccountLockedException("Acceso temporalmente restringido"))
                .when(loginAttemptService).checkLockout(anyString());

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@email.com", "Password123!"), CLIENT_IP))
                .isInstanceOf(AccountLockedException.class);

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void testLogin_InvalidPassword_RecordsFailure() {
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

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@email.com", "wrong"), CLIENT_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure("user@email.com");
    }

    @Test
    void testLogin_ValidCredentials_ResetsFailures() {
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
        when(jwtService.generateToken(eq("user@email.com"), any())).thenReturn("token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        authService.login(new LoginRequest("user@email.com", "Password123!"), CLIENT_IP);

        verify(loginAttemptService).resetFailures("user@email.com");
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

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

import com.vass.authentication.api.dto.RefreshRequest;
import com.vass.authentication.api.dto.RefreshResponse;
import com.vass.authentication.domain.exception.InvalidRefreshTokenException;
import com.vass.authentication.infrastructure.integration.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;
import com.vass.authentication.infrastructure.security.JwtService;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTokenTest {

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

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService,
                authorizationServiceClient, loginAttemptService);
    }

    @Test
    void testRefreshToken_InvalidToken_ThrowsUnauthorized() {
        when(jwtService.parseClaims("invalid-token")).thenThrow(new RuntimeException("invalid"));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshRequest("invalid-token")))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token inválido");
    }

    @Test
    void testRefreshToken_TokenNotRefreshType_ThrowsUnauthorized() {
        Claims claims = mock(Claims.class);
        when(jwtService.parseClaims("token"))
                .thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(new RefreshRequest("token")))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token inválido");
    }

    @Test
    void testRefreshToken_UserInactive_ThrowsUnauthorized() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("inactive@email.com");
        when(jwtService.parseClaims("token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("inactive@email.com")).thenReturn(
                Optional.of(UserEntity.builder()
                        .id(1L)
                        .email("inactive@email.com")
                        .passwordHash("hash")
                        .name("Inactive")
                        .active(false)
                        .role("VIEWER")
                        .build())
        );

        assertThatThrownBy(() -> authService.refreshToken(new RefreshRequest("token")))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token inválido");
    }

    @Test
    void testRefreshToken_ValidRefreshToken_ReturnsNewTokenPair() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user@email.com");
        when(jwtService.parseClaims("token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("user@email.com")).thenReturn(
                Optional.of(UserEntity.builder()
                        .id(2L)
                        .email("user@email.com")
                        .passwordHash("hash")
                        .name("User")
                        .active(true)
                        .role("VIEWER")
                        .build())
        );
        when(authorizationServiceClient.getPermissionsForUser(2L)).thenReturn(List.of("REPORT:READ"));
        when(jwtService.generateToken(eq("user@email.com"), any())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken("user@email.com")).thenReturn("new-refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(jwtService.getRefreshExpirationSeconds()).thenReturn(86400L);

        RefreshResponse response = authService.refreshToken(new RefreshRequest("token"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.refreshExpiresIn()).isEqualTo(86400L);
    }
}

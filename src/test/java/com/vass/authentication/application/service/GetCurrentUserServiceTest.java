package com.vass.authentication.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vass.authentication.api.dto.MeResponse;
import com.vass.authentication.domain.exception.InvalidCredentialsException;
import com.vass.authentication.infrastructure.integration.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthorizationServiceClient authorizationServiceClient;

    private GetCurrentUserService service;

    @BeforeEach
    void setUp() {
        service = new GetCurrentUserService(userRepository, authorizationServiceClient);
    }

    @Test
    void testGetCurrentUser_ValidUsername_ReturnsUsernameAndPermissions() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("demo@email.com")
                .passwordHash("hash")
                .name("Demo User")
                .active(true)
                .role("VIEWER")
                .build();

        when(userRepository.findByEmailIgnoreCase("demo@email.com")).thenReturn(Optional.of(user));
        when(authorizationServiceClient.getPermissionsForUser(1L)).thenReturn(List.of("REPORT:READ", "REPORT:DOWNLOAD"));

        MeResponse response = service.getCurrentUser("demo@email.com");

        assertThat(response.username()).isEqualTo("demo@email.com");
        assertThat(response.permissions()).containsExactlyInAnyOrder("REPORT:READ", "REPORT:DOWNLOAD");
    }

    @Test
    void testGetCurrentUser_NoPermissions_ReturnsEmptyList() {
        UserEntity user = UserEntity.builder()
                .id(2L)
                .email("noperm@email.com")
                .passwordHash("hash")
                .name("No Perm")
                .active(true)
                .role("VIEWER")
                .build();

        when(userRepository.findByEmailIgnoreCase("noperm@email.com")).thenReturn(Optional.of(user));
        when(authorizationServiceClient.getPermissionsForUser(2L)).thenReturn(List.of());

        MeResponse response = service.getCurrentUser("noperm@email.com");

        assertThat(response.username()).isEqualTo("noperm@email.com");
        assertThat(response.permissions()).isEmpty();
    }

    @Test
    void testGetCurrentUser_UserNotFound_ThrowsInvalidCredentialsException() {
        when(userRepository.findByEmailIgnoreCase("ghost@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser("ghost@email.com"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Token inválido o expirado");
    }
}

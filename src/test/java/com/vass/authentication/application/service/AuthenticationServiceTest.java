package com.vass.authentication.application.service;

import com.vass.authentication.api.dto.LoginRequest;
import com.vass.authentication.api.dto.LoginResponse;
import com.vass.authentication.domain.exception.InvalidCredentialsException;
import com.vass.authentication.domain.model.User;
import com.vass.authentication.infrastructure.client.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.client.dto.AssignPermissionRequest;
import com.vass.authentication.infrastructure.client.dto.AssignPermissionResponse;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponse;
import com.vass.authentication.infrastructure.repository.UserRepository;
import com.vass.authentication.infrastructure.security.JwtProperties;
import com.vass.authentication.infrastructure.security.JwtService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationServiceTest {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Test
    void shouldAuthenticateActiveUserWithValidCredentials() {
        User user = new User(1L, "user@acme.com", "demo", PASSWORD_ENCODER.encode("plain-password"), true);
        UserRepository userRepository = userRepositoryStub(user);

        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                return new UserPermissionsResponse(
                        userId,
                        List.of("REPORT:READ", " report:download ")
                );
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
            }
        };
        PermissionService permissionService = new PermissionService(client);
        JwtService jwtService = jwtService();
        AuthenticationService authenticationService = new AuthenticationService(
                userRepository,
                PASSWORD_ENCODER,
                permissionService,
                jwtService
        );

        LoginResponse response = authenticationService.authenticate(new LoginRequest("user@acme.com", "plain-password"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("demo");
        assertThat(response.accessToken()).isNotBlank();

        var claims = Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtProperties().getSecret().getBytes()))
                .build()
                .parseSignedClaims(response.accessToken())
                .getPayload();
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email", String.class)).isEqualTo("user@acme.com");
        assertThat((List<String>) claims.get("permissions")).containsExactly("REPORT:DOWNLOAD", "REPORT:READ");
    }

    @Test
    void shouldThrowUnauthorizedForInvalidCredentials() {
        UserRepository userRepository = userRepositoryStub(null);
        PermissionService permissionService = new PermissionService(new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                return new UserPermissionsResponse(userId, List.of());
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
            }
        });
        JwtService jwtService = jwtService();
        AuthenticationService authenticationService = new AuthenticationService(
                userRepository,
                PASSWORD_ENCODER,
                permissionService,
                jwtService
        );

        assertThatThrownBy(() -> authenticationService.authenticate(new LoginRequest("missing@acme.com", "password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldThrowUnauthorizedForInactiveUser() {
        User inactiveUser = new User(2L, "inactive@acme.com", "inactive", PASSWORD_ENCODER.encode("password"), false);
        UserRepository userRepository = userRepositoryStub(inactiveUser);
        PermissionService permissionService = new PermissionService(new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                return new UserPermissionsResponse(userId, List.of());
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
            }
        });
        JwtService jwtService = jwtService();
        AuthenticationService authenticationService = new AuthenticationService(
                userRepository,
                PASSWORD_ENCODER,
                permissionService,
                jwtService
        );

        assertThatThrownBy(() -> authenticationService.authenticate(new LoginRequest("inactive@acme.com", "password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldContinueWithEmptyPermissionsWhenFallbackHappens() {
        User user = new User(3L, "fallback@acme.com", "fallback", PASSWORD_ENCODER.encode("plain-password"), true);
        UserRepository userRepository = userRepositoryStub(user);

        AuthorizationServiceClient failingClient = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                throw feign.FeignException.errorStatus(
                        "getUserPermissions",
                        feign.Response.builder()
                                .status(503)
                                .reason("Service unavailable")
                                .request(feign.Request.create(
                                        feign.Request.HttpMethod.GET,
                                        "/api/permissions/users/" + userId,
                                        java.util.Map.of(),
                                        null,
                                        java.nio.charset.StandardCharsets.UTF_8,
                                        null
                                ))
                                .build()
                );
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
            }
        };
        PermissionService permissionService = new PermissionService(failingClient);
        JwtService jwtService = jwtService();
        AuthenticationService authenticationService = new AuthenticationService(
                userRepository,
                PASSWORD_ENCODER,
                permissionService,
                jwtService
        );

        LoginResponse response = authenticationService.authenticate(new LoginRequest("fallback@acme.com", "plain-password"));

        var claims = Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtProperties().getSecret().getBytes()))
                .build()
                .parseSignedClaims(response.accessToken())
                .getPayload();
        assertThat((List<String>) claims.get("permissions")).isEmpty();
    }

    private static UserRepository userRepositoryStub(User user) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class[]{UserRepository.class},
                (proxy, method, args) -> {
                    if ("findByEmailIgnoreCase".equals(method.getName())) {
                        String email = (String) args[0];
                        if (user != null && user.getEmail().equalsIgnoreCase(email)) {
                            return Optional.of(user);
                        }
                        return Optional.empty();
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "UserRepositoryStub";
                    }
                    throw new UnsupportedOperationException("Method not supported in stub: " + method.getName());
                }
        );
    }

    private static JwtService jwtService() {
        return new JwtService(jwtProperties());
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-at-least-thirty-two-bytes");
        properties.setExpirationSeconds(3600);
        return properties;
    }
}

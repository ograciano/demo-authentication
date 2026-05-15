package com.vass.authentication.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vass.authentication.domain.model.User;
import com.vass.authentication.infrastructure.client.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponse;
import com.vass.authentication.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        com.vass.authentication.AuthenticationApplication.class,
        AuthenticationControllerIntegrationTest.StubAuthorizationClientConfig.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StubAuthorizationServiceClient stubAuthorizationServiceClient;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        stubAuthorizationServiceClient.setPermissionMode(StubAuthorizationServiceClient.PermissionMode.SUCCESS);
        stubAuthorizationServiceClient.setAssignmentMode(StubAuthorizationServiceClient.AssignmentMode.ASSIGNED);
        stubAuthorizationServiceClient.resetAssignmentInvocations();
        stubAuthorizationServiceClient.setPermissions(List.of("REPORT:READ"));
        User user = new User(
                null,
                "john@acme.com",
                "john",
                passwordEncoder.encode("password123"),
                true
        );
        userRepository.save(user);
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "john@acme.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.username").value("john"));
    }

    @Test
    void shouldReturnBadRequestForInvalidEmailFormat() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "invalid-email",
                                "password", "password123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void shouldReturnUnauthorizedForWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "john@acme.com",
                                "password", "bad-pass"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void shouldLoginWithEmptyPermissionsWhenAuthorizationServiceFails() throws Exception {
        stubAuthorizationServiceClient.setPermissionMode(
                StubAuthorizationServiceClient.PermissionMode.SERVICE_UNAVAILABLE
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "john@acme.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void shouldLoginWithEmptyPermissionsWhenAuthorizationReturnsBadRequest() throws Exception {
        stubAuthorizationServiceClient.setPermissionMode(
                StubAuthorizationServiceClient.PermissionMode.BAD_REQUEST
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "john@acme.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void shouldLoginWithEmptyPermissionsWhenAuthorizationReturnsNotFound() throws Exception {
        stubAuthorizationServiceClient.setPermissionMode(
                StubAuthorizationServiceClient.PermissionMode.NOT_FOUND
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "john@acme.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void shouldLoginWithEmptyPermissionsWhenAuthorizationTimesOut() throws Exception {
        stubAuthorizationServiceClient.setPermissionMode(
                StubAuthorizationServiceClient.PermissionMode.TIMEOUT
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "john@acme.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void shouldRegisterSuccessfully() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Alice",
                                "lastName", "Smith",
                                "email", "alice@acme.com",
                                "password", "Password1!",
                                "passwordConfirm", "Password1!"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@acme.com"))
                .andExpect(jsonPath("$.role").value("REPORT_READER"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldRegisterSuccessfullyWhenPermissionAlreadyAssigned() throws Exception {
        stubAuthorizationServiceClient.setAssignmentMode(
                StubAuthorizationServiceClient.AssignmentMode.ALREADY_ASSIGNED
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Alice",
                                "lastName", "Smith",
                                "email", "already@acme.com",
                                "password", "Password1!",
                                "passwordConfirm", "Password1!"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("already@acme.com"));
    }

    @Test
    void shouldReturnConflictWhenRegisteringDuplicatedEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "John",
                                "lastName", "Doe",
                                "email", "john@acme.com",
                                "password", "Password1!",
                                "passwordConfirm", "Password1!"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));
        org.assertj.core.api.Assertions.assertThat(stubAuthorizationServiceClient.getAssignmentInvocations()).isZero();
    }

    @Test
    void shouldReturnBadRequestWhenPasswordConfirmDoesNotMatch() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Alice",
                                "lastName", "Smith",
                                "email", "alice@acme.com",
                                "password", "Password1!",
                                "passwordConfirm", "Password1?"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void shouldRollbackWhenPermissionAssignmentFails() throws Exception {
        stubAuthorizationServiceClient.setAssignmentMode(
                StubAuthorizationServiceClient.AssignmentMode.SERVICE_UNAVAILABLE
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rollback",
                                "lastName", "User",
                                "email", "rollback@acme.com",
                                "password", "Password1!",
                                "passwordConfirm", "Password1!"
                        ))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Permission assignment failed"));

        org.assertj.core.api.Assertions.assertThat(
                userRepository.findByEmailIgnoreCase("rollback@acme.com")
        ).isEmpty();
    }

    @Test
    void shouldRollbackWhenPermissionAssignmentReturnsBadRequest() throws Exception {
        stubAuthorizationServiceClient.setAssignmentMode(
                StubAuthorizationServiceClient.AssignmentMode.BAD_REQUEST
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rollback",
                                "lastName", "User",
                                "email", "rollback400@acme.com",
                                "password", "Password1!",
                                "passwordConfirm", "Password1!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Permission assignment failed"));

        org.assertj.core.api.Assertions.assertThat(
                userRepository.findByEmailIgnoreCase("rollback400@acme.com")
        ).isEmpty();
    }

    @Test
    void shouldRollbackWhenPermissionAssignmentReturnsNotFound() throws Exception {
        stubAuthorizationServiceClient.setAssignmentMode(
                StubAuthorizationServiceClient.AssignmentMode.NOT_FOUND
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rollback",
                                "lastName", "User",
                                "email", "rollback404@acme.com",
                                "password", "Password1!",
                                "passwordConfirm", "Password1!"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Permission assignment failed"));

        org.assertj.core.api.Assertions.assertThat(
                userRepository.findByEmailIgnoreCase("rollback404@acme.com")
        ).isEmpty();
    }

    @Test
    void shouldRollbackWhenPermissionAssignmentTimesOut() throws Exception {
        stubAuthorizationServiceClient.setAssignmentMode(
                StubAuthorizationServiceClient.AssignmentMode.TIMEOUT
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Rollback",
                                "lastName", "User",
                                "email", "rollback-timeout@acme.com",
                                "password", "Password1!",
                                "passwordConfirm", "Password1!"
                        ))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Permission assignment failed"));

        org.assertj.core.api.Assertions.assertThat(
                userRepository.findByEmailIgnoreCase("rollback-timeout@acme.com")
        ).isEmpty();
    }

    @TestConfiguration
    static class StubAuthorizationClientConfig {
        @Bean
        @Primary
        StubAuthorizationServiceClient stubAuthorizationServiceClient() {
            return new StubAuthorizationServiceClient();
        }
    }

    static class StubAuthorizationServiceClient implements AuthorizationServiceClient {
        enum PermissionMode {
            SUCCESS,
            SERVICE_UNAVAILABLE,
            BAD_REQUEST,
            NOT_FOUND,
            TIMEOUT
        }

        enum AssignmentMode {
            ASSIGNED,
            ALREADY_ASSIGNED,
            SERVICE_UNAVAILABLE,
            BAD_REQUEST,
            NOT_FOUND,
            TIMEOUT
        }

        private volatile List<String> permissions = List.of("REPORT:READ");
        private final AtomicReference<PermissionMode> permissionMode = new AtomicReference<>(PermissionMode.SUCCESS);
        private final AtomicReference<AssignmentMode> assignmentMode = new AtomicReference<>(AssignmentMode.ASSIGNED);
        private final java.util.concurrent.atomic.AtomicInteger assignmentInvocations = new java.util.concurrent.atomic.AtomicInteger(0);

        void setPermissions(List<String> permissions) {
            this.permissions = permissions;
        }

        void setPermissionMode(PermissionMode mode) {
            this.permissionMode.set(mode);
        }

        void setAssignmentMode(AssignmentMode mode) {
            this.assignmentMode.set(mode);
        }

        void resetAssignmentInvocations() {
            assignmentInvocations.set(0);
        }

        int getAssignmentInvocations() {
            return assignmentInvocations.get();
        }

        @Override
        public UserPermissionsResponse getUserPermissions(Long userId) {
            PermissionMode mode = permissionMode.get();
            if (mode == PermissionMode.TIMEOUT) {
                feign.Request request = feign.Request.create(
                        feign.Request.HttpMethod.GET,
                        "/api/permissions/users/" + userId,
                        Map.of(),
                        null,
                        java.nio.charset.StandardCharsets.UTF_8,
                        null
                );
                throw new feign.RetryableException(
                        503,
                        "timeout",
                        feign.Request.HttpMethod.GET,
                        new java.io.IOException("timeout"),
                        1000L,
                        request
                );
            }
            if (mode == PermissionMode.SERVICE_UNAVAILABLE || mode == PermissionMode.BAD_REQUEST || mode == PermissionMode.NOT_FOUND) {
                int status = switch (mode) {
                    case SERVICE_UNAVAILABLE -> 503;
                    case BAD_REQUEST -> 400;
                    case NOT_FOUND -> 404;
                    default -> 500;
                };
                throw feign.FeignException.errorStatus(
                        "getUserPermissions",
                        feign.Response.builder()
                                .status(status)
                                .reason(mode.name())
                                .request(feign.Request.create(
                                        feign.Request.HttpMethod.GET,
                                        "/api/permissions/users/" + userId,
                                        Map.of(),
                                        null,
                                        java.nio.charset.StandardCharsets.UTF_8,
                                        null
                                ))
                                .build()
                );
            }
            return new UserPermissionsResponse(userId, permissions);
        }

        @Override
        public com.vass.authentication.infrastructure.client.dto.AssignPermissionResponse assignPermission(
                Long userId,
                com.vass.authentication.infrastructure.client.dto.AssignPermissionRequest request
        ) {
            assignmentInvocations.incrementAndGet();
            AssignmentMode mode = assignmentMode.get();
            if (mode == AssignmentMode.TIMEOUT) {
                feign.Request timeoutRequest = feign.Request.create(
                        feign.Request.HttpMethod.POST,
                        "/api/permissions/users/" + userId,
                        Map.of(),
                        null,
                        java.nio.charset.StandardCharsets.UTF_8,
                        null
                );
                throw new feign.RetryableException(
                        503,
                        "timeout",
                        feign.Request.HttpMethod.POST,
                        new java.io.IOException("timeout"),
                        1000L,
                        timeoutRequest
                );
            }
            if (mode == AssignmentMode.SERVICE_UNAVAILABLE || mode == AssignmentMode.BAD_REQUEST || mode == AssignmentMode.NOT_FOUND) {
                int status = switch (mode) {
                    case SERVICE_UNAVAILABLE -> 503;
                    case BAD_REQUEST -> 400;
                    case NOT_FOUND -> 404;
                    default -> 500;
                };
                throw feign.FeignException.errorStatus(
                        "assignPermission",
                        feign.Response.builder()
                                .status(status)
                                .reason(mode.name())
                                .request(feign.Request.create(
                                        feign.Request.HttpMethod.POST,
                                        "/api/permissions/users/" + userId,
                                        Map.of(),
                                        null,
                                        java.nio.charset.StandardCharsets.UTF_8,
                                        null
                                ))
                                .build()
                );
            }
            String status = mode == AssignmentMode.ALREADY_ASSIGNED ? "ALREADY_ASSIGNED" : "ASSIGNED";
            return new com.vass.authentication.infrastructure.client.dto.AssignPermissionResponse(
                    userId,
                    request.permission(),
                    status
            );
        }
    }
}

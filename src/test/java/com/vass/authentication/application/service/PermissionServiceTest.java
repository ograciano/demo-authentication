package com.vass.authentication.application.service;

import com.vass.authentication.infrastructure.client.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.client.dto.AssignPermissionRequest;
import com.vass.authentication.infrastructure.client.dto.AssignPermissionResponse;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponse;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionServiceTest {

    @Test
    void shouldNormalizeAndSortPermissions() {
        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                return new UserPermissionsResponse(
                        userId,
                        Arrays.asList(" report:read ", "REPORT:DOWNLOAD", "report:read", null, "")
                );
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
            }
        };
        PermissionService permissionService = new PermissionService(client);

        List<String> permissions = permissionService.getUserPermissions(10L);

        assertThat(permissions).containsExactly("REPORT:DOWNLOAD", "REPORT:READ");
    }

    @Test
    void shouldFallbackToEmptyPermissionsWhenFeignTimeoutOccurs() {
        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                Request request = Request.create(
                        Request.HttpMethod.GET,
                        "/api/permissions/users/" + userId,
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null
                );
                throw new RetryableException(
                        503,
                        "timeout",
                        Request.HttpMethod.GET,
                        new IOException("timeout"),
                        1000L,
                        request
                );
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
            }
        };
        PermissionService permissionService = new PermissionService(client);

        List<String> permissions = permissionService.getUserPermissions(99L);

        assertThat(permissions).isEmpty();
    }

    @Test
    void shouldFallbackToEmptyPermissionsWhenFeignReturnsBadRequest() {
        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                throw feign.FeignException.errorStatus(
                        "getUserPermissions",
                        feign.Response.builder()
                                .status(400)
                                .reason("Bad request")
                                .request(feign.Request.create(
                                        feign.Request.HttpMethod.GET,
                                        "/api/permissions/users/" + userId,
                                        Map.of(),
                                        null,
                                        StandardCharsets.UTF_8,
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
        PermissionService permissionService = new PermissionService(client);

        List<String> permissions = permissionService.getUserPermissions(20L);

        assertThat(permissions).isEmpty();
    }

    @Test
    void shouldFallbackToEmptyPermissionsWhenFeignReturnsNotFound() {
        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                throw feign.FeignException.errorStatus(
                        "getUserPermissions",
                        feign.Response.builder()
                                .status(404)
                                .reason("Not Found")
                                .request(feign.Request.create(
                                        feign.Request.HttpMethod.GET,
                                        "/api/permissions/users/" + userId,
                                        Map.of(),
                                        null,
                                        StandardCharsets.UTF_8,
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
        PermissionService permissionService = new PermissionService(client);

        List<String> permissions = permissionService.getUserPermissions(21L);

        assertThat(permissions).isEmpty();
    }

    @Test
    void shouldReturnEmptyAndSkipRemoteLookupWhenUserIdIsInvalid() {
        AtomicInteger invocations = new AtomicInteger(0);
        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                invocations.incrementAndGet();
                return new UserPermissionsResponse(userId, List.of("REPORT:READ"));
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
            }
        };
        PermissionService permissionService = new PermissionService(client);

        List<String> permissions = permissionService.getUserPermissions(0L);

        assertThat(permissions).isEmpty();
        assertThat(invocations.get()).isZero();
    }
}

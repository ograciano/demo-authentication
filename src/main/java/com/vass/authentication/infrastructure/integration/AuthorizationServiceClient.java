package com.vass.authentication.infrastructure.integration;

import com.vass.authentication.domain.exception.PermissionsServiceException;
import com.vass.authentication.infrastructure.integration.dto.PermissionAssignmentRequest;
import com.vass.authentication.infrastructure.integration.dto.PermissionAssignmentResponse;
import com.vass.authentication.infrastructure.integration.dto.UserPermissionsResponse;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AuthorizationServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServiceClient.class);
    private static final String ASSIGNED = "ASSIGNED";
    private static final String ALREADY_ASSIGNED = "ALREADY_ASSIGNED";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AuthorizationServiceClient(RestTemplate restTemplate,
                                      @Value("${app.authorization.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public List<String> getPermissionsForUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be greater than 0");
        }
        try {
            RequestEntity<Void> request = new RequestEntity<>(HttpMethod.GET,
                    URI.create(baseUrl + "/api/permissions/users/" + userId));
            var response = restTemplate.exchange(request, UserPermissionsResponse.class);
            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                LOGGER.warn("Permissions query fallback for userId={} due to non-success status {}", userId, status.value());
                return List.of();
            }
            UserPermissionsResponse body = response.getBody();
            if (body == null || body.permissions() == null) {
                return List.of();
            }
            return body.permissions();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOGGER.warn("Permissions query returned 404 for userId={}, using empty permissions fallback", userId);
                return List.of();
            }
            LOGGER.warn("Permissions query fallback for userId={} due to status {}", userId, ex.getStatusCode().value());
            return List.of();
        } catch (RestClientException ex) {
            LOGGER.warn("Permissions query fallback for userId={} due to client error {}", userId, ex.getClass().getSimpleName());
            return List.of();
        }
    }

    public void assignInitialPermission(Long userId, String permission) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be greater than 0");
        }
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("permission must not be blank");
        }
        String normalizedPermission = permission.trim().toUpperCase();

        try {
            RequestEntity<PermissionAssignmentRequest> request = RequestEntity
                    .post(URI.create(baseUrl + "/api/permissions/users/" + userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PermissionAssignmentRequest(normalizedPermission));

            var response = restTemplate.exchange(request, PermissionAssignmentResponse.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new PermissionsServiceException("Permission bootstrap failed with non-success status", null);
            }

            PermissionAssignmentResponse body = response.getBody();
            if (body == null || body.status() == null) {
                throw new PermissionsServiceException("Permission bootstrap returned invalid payload", null);
            }

            String normalizedStatus = body.status().trim().toUpperCase();
            if (!ASSIGNED.equals(normalizedStatus) && !ALREADY_ASSIGNED.equals(normalizedStatus)) {
                throw new PermissionsServiceException("Permission bootstrap returned unsupported status", null);
            }
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 400 || statusCode == 404) {
                LOGGER.warn("Permission bootstrap contract error status={} for userId={}", statusCode, userId);
                throw new PermissionsServiceException("Permission bootstrap contract error status " + statusCode, ex);
            }
            LOGGER.warn("Permission bootstrap dependency error status={} for userId={}", statusCode, userId);
            throw new PermissionsServiceException("Permission bootstrap dependency error status " + statusCode, ex);
        } catch (RestClientException ex) {
            LOGGER.warn("Permission bootstrap dependency error for userId={} type={}", userId, ex.getClass().getSimpleName());
            throw new PermissionsServiceException("Unable to assign initial permission in authorization-service", ex);
        }
    }
}

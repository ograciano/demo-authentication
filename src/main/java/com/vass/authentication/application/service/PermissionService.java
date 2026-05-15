package com.vass.authentication.application.service;

import com.vass.authentication.infrastructure.client.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponse;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;

@Service
public class PermissionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionService.class);
    private final AuthorizationServiceClient authorizationServiceClient;

    public PermissionService(AuthorizationServiceClient authorizationServiceClient) {
        this.authorizationServiceClient = authorizationServiceClient;
    }

    public List<String> getUserPermissions(Long userId) {
        if (userId == null || userId <= 0) {
            LOGGER.warn("Skipping permission lookup due to invalid userId={}", userId);
            return Collections.emptyList();
        }
        try {
            UserPermissionsResponse response = authorizationServiceClient.getUserPermissions(userId);
            if (response == null || response.permissions() == null) {
                return Collections.emptyList();
            }
            TreeSet<String> normalized = new TreeSet<>();
            for (String permission : response.permissions()) {
                if (permission == null) {
                    continue;
                }
                String cleaned = permission.trim();
                if (!cleaned.isBlank()) {
                    normalized.add(cleaned.toUpperCase(Locale.ROOT));
                }
            }
            return normalized.stream().filter(Objects::nonNull).toList();
        } catch (FeignException ex) {
            LOGGER.warn("Permission lookup fallback for userId={} reason={}", userId, ex.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }
}

package com.vass.authentication.infrastructure.integration.dto;

import java.util.List;

public record UserPermissionsResponse(Long userId, List<String> permissions, String timestamp) {
}

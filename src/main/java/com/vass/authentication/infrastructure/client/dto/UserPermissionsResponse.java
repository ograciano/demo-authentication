package com.vass.authentication.infrastructure.client.dto;

import java.util.List;

public record UserPermissionsResponse(
        Long userId,
        List<String> permissions
) {
}

package com.vass.authentication.infrastructure.client.dto;

public record AssignPermissionResponse(
        Long userId,
        String permission,
        String status
) {
}

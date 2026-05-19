package com.vass.authentication.infrastructure.integration.dto;

public record PermissionAssignmentResponse(Long userId, String permission, String status) {
}

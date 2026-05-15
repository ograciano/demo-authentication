package com.vass.authentication.api.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String error,
        String message,
        Instant timestamp,
        Map<String, String> details
) {
}

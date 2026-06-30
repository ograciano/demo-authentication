package com.vass.authentication.api.dto;

import java.util.List;

public record MeResponse(
        String username,
        List<String> permissions
) {
}

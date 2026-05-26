package com.vass.authentication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record LoginAttemptProperties(
        RateLimit rateLimit,
        Lockout lockout
) {

    public record RateLimit(
            int maxAttemptsPerIp,
            int maxAttemptsPerEmail,
            long windowSeconds
    ) {}

    public record Lockout(
            int maxFailures,
            long durationSeconds
    ) {}
}

package com.vass.authentication.infrastructure.security;

import com.vass.authentication.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.expirationSeconds();
    }

    public String generateToken(String subject, List<String> permissions) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expirationSeconds);
        return Jwts.builder()
                .subject(subject)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}

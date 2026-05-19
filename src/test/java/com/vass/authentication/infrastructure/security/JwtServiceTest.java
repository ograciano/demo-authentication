package com.vass.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.vass.authentication.config.JwtProperties;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void testGenerateToken_ContainsRequiredClaimsAndExpiration() {
        JwtService jwtService = new JwtService(new JwtProperties("01234567890123456789012345678901", 3600));

        String token = jwtService.generateToken("user@email.com", List.of("REPORT:READ"));
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user@email.com");
        assertThat(claims.get("permissions", List.class)).containsExactly("REPORT:READ");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }
}

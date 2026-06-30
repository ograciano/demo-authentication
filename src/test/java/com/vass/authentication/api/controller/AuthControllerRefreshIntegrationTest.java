package com.vass.authentication.api.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import com.vass.authentication.infrastructure.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.authorization.base-url=http://authorization-service")
class AuthControllerRefreshIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JwtService jwtService;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    @Test
    void testRefresh_ValidToken_ReturnsNewTokens() throws Exception {
        String refreshToken = jwtService.generateRefreshToken("oscar.demo@email.com");

        mockServer.expect(requestTo("http://authorization-service/api/permissions/users/1"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"userId\":1,\"permissions\":[\"REPORT:READ\"],\"timestamp\":\"2026-05-15T21:00:00Z\"}", APPLICATION_JSON));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.expiresIn", is(3600)))
                .andExpect(jsonPath("$.refreshExpiresIn", is(86400)));

        mockServer.verify();
    }

    @Test
    void testRefresh_InvalidToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\": \"invalid-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Refresh token inválido")));
    }
}

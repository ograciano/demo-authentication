package com.vass.authentication.api.controller;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;
import com.vass.authentication.application.service.LoginAttemptService;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.authorization.base-url=http://authorization-service")
class AuthControllerMeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginAttemptService loginAttemptService;

    private MockRestServiceServer mockServer;
    private String validToken;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        loginAttemptService.resetAll();

        userId = userRepository.findByEmailIgnoreCase("oscar.demo@email.com").orElseThrow().getId();

        // Login to obtain a valid JWT for use in /me tests
        mockServer.expect(requestTo("http://authorization-service/api/permissions/users/" + userId))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "userId": %d,
                          "permissions": ["REPORT:READ"],
                          "timestamp": "2026-06-01T00:00:00Z"
                        }
                        """.formatted(userId), APPLICATION_JSON));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "oscar.demo@email.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        validToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
        mockServer.verify();
        mockServer.reset();
    }

    @Test
    void testGetMe_ValidToken_Returns200WithUsernameAndPermissions() throws Exception {
        mockServer.expect(requestTo("http://authorization-service/api/permissions/users/" + userId))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "userId": %d,
                          "permissions": ["REPORT:READ"],
                          "timestamp": "2026-06-01T00:00:00Z"
                        }
                        """.formatted(userId), APPLICATION_JSON));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("oscar.demo@email.com")))
                .andExpect(jsonPath("$.permissions[0]", is("REPORT:READ")));

        mockServer.verify();
    }

    @Test
    void testGetMe_ValidTokenAuthServiceFails_Returns200WithEmptyPermissions() throws Exception {
        mockServer.expect(requestTo("http://authorization-service/api/permissions/users/" + userId))
                .andExpect(method(GET))
                .andRespond(withServerError());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("oscar.demo@email.com")))
                .andExpect(jsonPath("$.permissions", is(not(emptyOrNullString()))));

        mockServer.verify();
    }

    @Test
    void testGetMe_NoAuthorizationHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetMe_InvalidToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetMe_MalformedBearerPrefix_Returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetMe_ValidToken_UsernameMatchesSubClaim() throws Exception {
        mockServer.expect(requestTo("http://authorization-service/api/permissions/users/" + userId))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "userId": %d,
                          "permissions": [],
                          "timestamp": "2026-06-01T00:00:00Z"
                        }
                        """.formatted(userId), APPLICATION_JSON));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", not(emptyOrNullString())));

        mockServer.verify();
    }
}

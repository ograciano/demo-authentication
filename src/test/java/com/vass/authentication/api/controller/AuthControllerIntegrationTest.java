package com.vass.authentication.api.controller;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;
import com.vass.authentication.infrastructure.security.JwtService;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.authorization.base-url=http://authorization-service")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    @Test
    void testLogin_ValidCredentials_Returns200AndJwt() throws Exception {
        Long userId = userRepository.findByEmailIgnoreCase("oscar.demo@email.com").orElseThrow().getId();
        mockServer.expect(requestTo("http://authorization-service/api/permissions/users/" + userId))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "userId": %d,
                          "permissions": ["REPORT:READ", "REPORT:DOWNLOAD", "REPORT:READ"],
                          "timestamp": "2026-05-15T21:00:00Z"
                        }
                        """.formatted(userId), APPLICATION_JSON));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "oscar.demo@email.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.expiresIn", is(3600)))
                .andExpect(jsonPath("$.user.email", is("oscar.demo@email.com")));

        mockServer.verify();
    }

    @Test
    void testLogin_InvalidPayload_Returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/auth/login")));
    }

    @Test
    void testLogin_WrongPassword_Returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "oscar.demo@email.com",
                                  "password": "WrongPassword"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Credenciales inválidas")));
    }

    @Test
    void testLogin_InactiveUser_Returns403() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "inactive.demo@email.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    void testLogin_PermissionsServiceUnavailable_Returns200WithEmptyPermissionsFallback() throws Exception {
        Long userId = userRepository.findByEmailIgnoreCase("oscar.demo@email.com").orElseThrow().getId();
        mockServer.expect(requestTo("http://authorization-service/api/permissions/users/" + userId))
                .andExpect(method(GET))
                .andRespond(withServerError());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "oscar.demo@email.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andReturn();

        assertTokenPermissions(result, List.of());

        mockServer.verify();
    }

    @Test
    void testLogin_EmptyPermissions_StillReturns200() throws Exception {
        Long userId = userRepository.findByEmailIgnoreCase("oscar.demo@email.com").orElseThrow().getId();
        mockServer.expect(requestTo("http://authorization-service/api/permissions/users/" + userId))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "userId": %d,
                          "permissions": [],
                          "timestamp": "2026-05-15T21:00:00Z"
                        }
                        """.formatted(userId), APPLICATION_JSON));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "oscar.demo@email.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andReturn();

        assertTokenPermissions(result, List.of());

        mockServer.verify();
    }

    @Test
    void testLogin_PermissionsServiceReturns404_Returns200WithEmptyPermissionsFallback() throws Exception {
        Long userId = userRepository.findByEmailIgnoreCase("oscar.demo@email.com").orElseThrow().getId();
        mockServer.expect(requestTo("http://authorization-service/api/permissions/users/" + userId))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "oscar.demo@email.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andReturn();

        assertTokenPermissions(result, List.of());

        mockServer.verify();
    }

    @Test
    void testRegister_ValidPayload_Returns201AndPersistsSecurely() throws Exception {
        String email = "register.success@email.com";

        mockServer.expect(requestTo(startsWith("http://authorization-service/api/permissions/users/")))
                .andExpect(method(POST))
                .andExpect(content().json("{\"permission\":\"REPORT:READ\"}"))
                .andRespond(withSuccess("""
                        {
                          "userId": 100,
                          "permission": "REPORT:READ",
                          "status": "ASSIGNED"
                        }
                        """, APPLICATION_JSON));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Register Success",
                                  "email": "%s",
                                  "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.roles[0]", is("VIEWER")));

        UserEntity created = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(created.isActive()).isTrue();
        org.assertj.core.api.Assertions.assertThat(created.getRole()).isEqualTo("VIEWER");
        org.assertj.core.api.Assertions.assertThat(created.getPasswordHash()).isNotEqualTo("Password123!");

        mockServer.verify();
    }

    @Test
    void testRegister_PermissionAlreadyAssigned_Returns201() throws Exception {
        String email = "register.already@email.com";

        mockServer.expect(requestTo(startsWith("http://authorization-service/api/permissions/users/")))
                .andExpect(method(POST))
                .andExpect(content().json("{\"permission\":\"REPORT:READ\"}"))
                .andRespond(withSuccess("""
                        {
                          "userId": 101,
                          "permission": "REPORT:READ",
                          "status": "ALREADY_ASSIGNED"
                        }
                        """, APPLICATION_JSON));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Already Assigned",
                                  "email": "%s",
                                  "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is(email)));

        org.assertj.core.api.Assertions.assertThat(userRepository.findByEmailIgnoreCase(email)).isPresent();
        mockServer.verify();
    }

    @Test
    void testRegister_InvalidPayload_Returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "email": "bad-email",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/auth/register")));
    }

    @Test
    void testRegister_DuplicateEmail_Returns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Duplicate",
                                  "email": "oscar.demo@email.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("El correo ya se encuentra registrado")));
    }

    @Test
    void testRegister_PermissionBootstrapFailure_RollsBackUserAndReturns503() throws Exception {
        String email = "register.rollback@email.com";
        long usersBefore = userRepository.count();

        mockServer.expect(requestTo(startsWith("http://authorization-service/api/permissions/users/")))
                .andExpect(method(POST))
                .andExpect(content().json("{\"permission\":\"REPORT:READ\"}"))
                .andRespond(withServerError());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Rollback",
                                  "email": "%s",
                                  "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.path", is("/api/auth/register")));

        org.assertj.core.api.Assertions.assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(userRepository.count()).isEqualTo(usersBefore);

        mockServer.verify();
    }

    @Test
    void testRegister_PermissionBootstrapBadRequest_RollsBackUserAndReturns503() throws Exception {
        String email = "register.badrequest@email.com";
        long usersBefore = userRepository.count();

        mockServer.expect(requestTo(startsWith("http://authorization-service/api/permissions/users/")))
                .andExpect(method(POST))
                .andExpect(content().json("{\"permission\":\"REPORT:READ\"}"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad Request Rollback",
                                  "email": "%s",
                                  "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.path", is("/api/auth/register")));

        org.assertj.core.api.Assertions.assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(userRepository.count()).isEqualTo(usersBefore);
        mockServer.verify();
    }

    @Test
    void testRegister_PermissionBootstrapNotFound_RollsBackUserAndReturns503() throws Exception {
        String email = "register.notfound@email.com";
        long usersBefore = userRepository.count();

        mockServer.expect(requestTo(startsWith("http://authorization-service/api/permissions/users/")))
                .andExpect(method(POST))
                .andExpect(content().json("{\"permission\":\"REPORT:READ\"}"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Not Found Rollback",
                                  "email": "%s",
                                  "password": "Password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.path", is("/api/auth/register")));

        org.assertj.core.api.Assertions.assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(userRepository.count()).isEqualTo(usersBefore);
        mockServer.verify();
    }

    private void assertTokenPermissions(MvcResult result, List<String> expectedPermissions) throws Exception {
        String responseJson = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseJson, "$.accessToken");
        Claims claims = jwtService.parseClaims(accessToken);
        org.assertj.core.api.Assertions.assertThat(claims.get("permissions", List.class))
                .containsExactlyElementsOf(expectedPermissions);
    }
}

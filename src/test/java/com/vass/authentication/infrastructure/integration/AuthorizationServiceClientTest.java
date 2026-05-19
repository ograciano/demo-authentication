package com.vass.authentication.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vass.authentication.domain.exception.PermissionsServiceException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AuthorizationServiceClientTest {

    private RestTemplate restTemplate;
    private AuthorizationServiceClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        client = new AuthorizationServiceClient(restTemplate, "http://authorization-service");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void testGetPermissionsForUser_UserIdInvalid_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> client.getPermissionsForUser(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be greater than 0");
    }

    @Test
    void testGetPermissionsForUser_ValidResponse_ReturnsPermissions() {
        server.expect(requestTo("http://authorization-service/api/permissions/users/10"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "userId": 10,
                          "permissions": ["REPORT:READ", "REPORT:DOWNLOAD"],
                          "timestamp": "2026-05-15T21:00:00Z"
                        }
                        """, APPLICATION_JSON));

        List<String> permissions = client.getPermissionsForUser(10L);

        assertThat(permissions).containsExactly("REPORT:READ", "REPORT:DOWNLOAD");
        server.verify();
    }

    @Test
    void testGetPermissionsForUser_NotFound_ReturnsEmptyListFallback() {
        server.expect(requestTo("http://authorization-service/api/permissions/users/11"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        List<String> permissions = client.getPermissionsForUser(11L);

        assertThat(permissions).isEmpty();
        server.verify();
    }

    @Test
    void testGetPermissionsForUser_ServerError_ReturnsEmptyListFallback() {
        server.expect(requestTo("http://authorization-service/api/permissions/users/12"))
                .andExpect(method(GET))
                .andRespond(withServerError());

        List<String> permissions = client.getPermissionsForUser(12L);

        assertThat(permissions).isEmpty();
        server.verify();
    }

    @Test
    void testAssignInitialPermission_InvalidUserId_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> client.assignInitialPermission(0L, "REPORT:READ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be greater than 0");
    }

    @Test
    void testAssignInitialPermission_BlankPermission_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> client.assignInitialPermission(10L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("permission must not be blank");
    }

    @Test
    void testAssignInitialPermission_AssignedStatus_ReturnsSuccess() {
        server.expect(requestTo("http://authorization-service/api/permissions/users/10"))
                .andExpect(method(POST))
                .andExpect(content().json("{\"permission\":\"REPORT:READ\"}"))
                .andRespond(withSuccess("""
                        {
                          "userId": 10,
                          "permission": "REPORT:READ",
                          "status": "ASSIGNED"
                        }
                        """, APPLICATION_JSON));

        assertThatCode(() -> client.assignInitialPermission(10L, "report:read"))
                .doesNotThrowAnyException();

        server.verify();
    }

    @Test
    void testAssignInitialPermission_AlreadyAssignedStatus_ReturnsSuccess() {
        server.expect(requestTo("http://authorization-service/api/permissions/users/10"))
                .andExpect(method(POST))
                .andExpect(content().json("{\"permission\":\"REPORT:READ\"}"))
                .andRespond(withSuccess("""
                        {
                          "userId": 10,
                          "permission": "REPORT:READ",
                          "status": "ALREADY_ASSIGNED"
                        }
                        """, APPLICATION_JSON));

        assertThatCode(() -> client.assignInitialPermission(10L, "REPORT:READ"))
                .doesNotThrowAnyException();

        server.verify();
    }

    @Test
    void testAssignInitialPermission_BadRequest_ThrowsPermissionsServiceException() {
        server.expect(requestTo("http://authorization-service/api/permissions/users/10"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.assignInitialPermission(10L, "REPORT:READ"))
                .isInstanceOf(PermissionsServiceException.class)
                .hasMessageContaining("status 400");

        server.verify();
    }

    @Test
    void testAssignInitialPermission_UserNotFound_ThrowsPermissionsServiceException() {
        server.expect(requestTo("http://authorization-service/api/permissions/users/10"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.assignInitialPermission(10L, "REPORT:READ"))
                .isInstanceOf(PermissionsServiceException.class)
                .hasMessageContaining("status 404");

        server.verify();
    }

    @Test
    void testAssignInitialPermission_ServerError_ThrowsPermissionsServiceException() {
        server.expect(requestTo("http://authorization-service/api/permissions/users/10"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.assignInitialPermission(10L, "REPORT:READ"))
                .isInstanceOf(PermissionsServiceException.class)
                .hasMessageContaining("status 500");

        server.verify();
    }
}

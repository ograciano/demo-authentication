package com.vass.authentication.application.service;

import com.vass.authentication.api.dto.RegisterRequest;
import com.vass.authentication.api.dto.RegisterResponse;
import com.vass.authentication.domain.exception.EmailAlreadyExistsException;
import com.vass.authentication.domain.exception.PermissionAssignmentException;
import com.vass.authentication.domain.model.User;
import com.vass.authentication.infrastructure.client.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.client.dto.AssignPermissionRequest;
import com.vass.authentication.infrastructure.client.dto.AssignPermissionResponse;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponse;
import com.vass.authentication.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class UserRegistrationServiceTest {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Test
    void shouldRegisterUserSuccessfully() {
        InMemoryUserStore store = new InMemoryUserStore();
        UserRepository repository = userRepositoryStub(store);
        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                return new UserPermissionsResponse(userId, java.util.List.of("REPORT:READ"));
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
            }
        };
        UserRegistrationService service = new UserRegistrationService(repository, PASSWORD_ENCODER, client, "REPORT:READ");

        RegisterResponse response = service.register(new RegisterRequest(
                "Jane",
                "Doe",
                "jane@acme.com",
                "Password1!",
                "Password1!"
        ));

        assertThat(response.userId()).isNotNull();
        assertThat(response.email()).isEqualTo("jane@acme.com");
        assertThat(response.role()).isEqualTo("REPORT_READER");
        assertThat(response.active()).isTrue();

        User persisted = store.usersByEmail.get("jane@acme.com");
        assertThat(persisted).isNotNull();
        assertThat(persisted.getPasswordHash()).isNotEqualTo("Password1!");
        assertThat(PASSWORD_ENCODER.matches("Password1!", persisted.getPasswordHash())).isTrue();
    }

    @Test
    void shouldRejectDuplicateEmail() {
        InMemoryUserStore store = new InMemoryUserStore();
        store.save(new User(null, "jane@acme.com", "Jane", "Jane", "Doe", "REPORT_READER", PASSWORD_ENCODER.encode("Password1!"), true));
        UserRepository repository = userRepositoryStub(store);
        AuthorizationServiceClient client = new NoopAuthorizationClient();
        UserRegistrationService service = new UserRegistrationService(repository, PASSWORD_ENCODER, client, "REPORT:READ");

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "Jane", "Doe", "jane@acme.com", "Password1!", "Password1!"
        ))).isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void shouldThrowPermissionAssignmentExceptionWhenRemoteFails() {
        InMemoryUserStore store = new InMemoryUserStore();
        UserRepository repository = userRepositoryStub(store);
        AuthorizationServiceClient failingClient = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                return new UserPermissionsResponse(userId, java.util.List.of());
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                throw feign.FeignException.errorStatus(
                        "assignPermission",
                        feign.Response.builder()
                                .status(503)
                                .reason("Service unavailable")
                                .request(feign.Request.create(
                                        feign.Request.HttpMethod.POST,
                                        "/api/permissions/users/" + userId,
                                        Map.of(),
                                        null,
                                        java.nio.charset.StandardCharsets.UTF_8,
                                        null
                                ))
                                .build()
                );
            }
        };
        UserRegistrationService service = new UserRegistrationService(repository, PASSWORD_ENCODER, failingClient, "REPORT:READ");

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "Jane", "Doe", "jane@acme.com", "Password1!", "Password1!"
        ))).isInstanceOf(PermissionAssignmentException.class);
    }

    @Test
    void shouldRegisterSuccessfullyWhenPermissionAlreadyAssigned() {
        InMemoryUserStore store = new InMemoryUserStore();
        UserRepository repository = userRepositoryStub(store);
        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                return new UserPermissionsResponse(userId, java.util.List.of());
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                return new AssignPermissionResponse(userId, request.permission(), "ALREADY_ASSIGNED");
            }
        };
        UserRegistrationService service = new UserRegistrationService(repository, PASSWORD_ENCODER, client, "REPORT:READ");

        RegisterResponse response = service.register(new RegisterRequest(
                "Jane", "Doe", "jane@acme.com", "Password1!", "Password1!"
        ));

        assertThat(response.userId()).isNotNull();
        assertThat(store.usersByEmail).containsKey("jane@acme.com");
    }

    @Test
    void shouldMapFunctionalRemoteErrorsToProperHttpStatus() {
        InMemoryUserStore store = new InMemoryUserStore();
        UserRepository repository = userRepositoryStub(store);
        AtomicReference<Integer> statusToThrow = new AtomicReference<>(400);
        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                return new UserPermissionsResponse(userId, java.util.List.of());
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                int status = statusToThrow.get();
                throw feign.FeignException.errorStatus(
                        "assignPermission",
                        feign.Response.builder()
                                .status(status)
                                .reason("error")
                                .request(feign.Request.create(
                                        feign.Request.HttpMethod.POST,
                                        "/api/permissions/users/" + userId,
                                        Map.of(),
                                        null,
                                        java.nio.charset.StandardCharsets.UTF_8,
                                        null
                                ))
                                .build()
                );
            }
        };
        UserRegistrationService service = new UserRegistrationService(repository, PASSWORD_ENCODER, client, "REPORT:READ");

        PermissionAssignmentException badRequest = (PermissionAssignmentException) catchThrowable(() ->
                service.register(new RegisterRequest("Jane", "Doe", "jane@acme.com", "Password1!", "Password1!"))
        );
        assertThat(badRequest).isNotNull();
        assertThat(badRequest.getStatus().value()).isEqualTo(400);

        statusToThrow.set(404);
        PermissionAssignmentException notFound = (PermissionAssignmentException) catchThrowable(() ->
                service.register(new RegisterRequest("Mary", "Doe", "mary@acme.com", "Password1!", "Password1!"))
        );
        assertThat(notFound).isNotNull();
        assertThat(notFound.getStatus().value()).isEqualTo(404);
    }

    @Test
    void shouldNotInvokeRemoteAssignmentForDuplicateEmail() {
        InMemoryUserStore store = new InMemoryUserStore();
        store.save(new User(null, "jane@acme.com", "Jane", "Jane", "Doe", "REPORT_READER", PASSWORD_ENCODER.encode("Password1!"), true));
        UserRepository repository = userRepositoryStub(store);
        AtomicLong invocations = new AtomicLong(0);
        AuthorizationServiceClient client = new AuthorizationServiceClient() {
            @Override
            public UserPermissionsResponse getUserPermissions(Long userId) {
                return new UserPermissionsResponse(userId, java.util.List.of());
            }

            @Override
            public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
                invocations.incrementAndGet();
                return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
            }
        };
        UserRegistrationService service = new UserRegistrationService(repository, PASSWORD_ENCODER, client, "REPORT:READ");

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "Jane", "Doe", "jane@acme.com", "Password1!", "Password1!"
        ))).isInstanceOf(EmailAlreadyExistsException.class);
        assertThat(invocations.get()).isZero();
    }

    private static UserRepository userRepositoryStub(InMemoryUserStore store) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class[]{UserRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "existsByEmailIgnoreCase" -> {
                            return store.usersByEmail.containsKey(((String) args[0]).toLowerCase());
                        }
                        case "save" -> {
                            return store.save((User) args[0]);
                        }
                        case "findByEmailIgnoreCase" -> {
                            return java.util.Optional.ofNullable(store.usersByEmail.get(((String) args[0]).toLowerCase()));
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "equals" -> {
                            return proxy == args[0];
                        }
                        case "toString" -> {
                            return "UserRepositoryStub";
                        }
                        default -> throw new UnsupportedOperationException("Method not supported in stub: " + method.getName());
                    }
                }
        );
    }

    private static final class InMemoryUserStore {
        private final Map<String, User> usersByEmail = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong(0);

        private User save(User user) {
            Long id = user.getId() == null ? ids.incrementAndGet() : user.getId();
            User persisted = new User(
                    id,
                    user.getEmail(),
                    user.getUsername(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getRole(),
                    user.getPasswordHash(),
                    user.isActive()
            );
            usersByEmail.put(persisted.getEmail().toLowerCase(), persisted);
            return persisted;
        }
    }

    private static final class NoopAuthorizationClient implements AuthorizationServiceClient {
        @Override
        public UserPermissionsResponse getUserPermissions(Long userId) {
            return new UserPermissionsResponse(userId, java.util.List.of());
        }

        @Override
        public AssignPermissionResponse assignPermission(Long userId, AssignPermissionRequest request) {
            return new AssignPermissionResponse(userId, request.permission(), "ASSIGNED");
        }
    }
}

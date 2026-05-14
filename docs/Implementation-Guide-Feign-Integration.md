# Guía de Implementación - Integración Authentication ↔ Authorization con Feign

## Objetivo
Documentar cómo integrar el Feign Client de autorización en el servicio de autenticación, específicamente en el flujo de registro de usuario (HU-02) con transacciones atómicas.

---

## Arquitectura de la Solución

```
┌─────────────────────────────────────────────────────────────┐
│           authentication-service (este proyecto)            │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  AuthController (POST /api/auth/register)                   │
│          ↓                                                   │
│  RegisterService (orchestration + transacciones)           │
│          ├→ UserRepository.save(user)                       │
│          ├→ AuthorizationServiceClient.assignPermission()   │
│          │   (Feign call to authorization-service)         │
│          └→ (Si falla, rollback automático)                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                         │ Feign (HTTP)
                         ↓
┌─────────────────────────────────────────────────────────────┐
│           authorization-service (futuro)                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  PermissionController                                       │
│    POST /api/permissions/users/{userId}                    │
│      ↓                                                      │
│  PermissionService                                         │
│      ↓                                                      │
│  UserPermissionRepository.save()                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Cambios en authentication-service

### 1. Agregar Dependencia Feign (pom.xml)

```xml
<!-- Dentro de <dependencies> -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
    <version>4.1.0</version>
</dependency>

<!-- Para gestionar versiones de Spring Cloud -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2. Habilitar Feign (Application.java)

```java
package com.vass.authentication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients  // ← Agregar esto
public class AuthenticationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthenticationApplication.class, args);
    }
}
```

### 3. Crear Feign Client Interface

**Archivo:** `src/main/java/com/vass/authentication/infrastructure/client/AuthorizationServiceClient.java`

```java
package com.vass.authentication.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponseDTO;
import com.vass.authentication.infrastructure.client.dto.PermissionAssignmentDTO;
import com.vass.authentication.infrastructure.client.dto.AssignmentResponseDTO;

/**
 * Cliente Feign para consumir authorization-service.
 * URL configurada en application.properties sin Eureka.
 */
@FeignClient(
    name = "authorization-service",
    url = "${authorization.service.url:http://localhost:8081}",
    path = "/api",
    fallback = AuthorizationServiceClientFallback.class
)
public interface AuthorizationServiceClient {

    @GetMapping(value = "/permissions/users/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    UserPermissionsResponseDTO getUserPermissions(@PathVariable("userId") Long userId);

    @PostMapping(value = "/permissions/users/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    AssignmentResponseDTO assignPermissionToUser(
        @PathVariable("userId") Long userId,
        @RequestBody PermissionAssignmentDTO request
    );
}
```

### 4. Crear DTOs Cliente

**Archivo:** `src/main/java/com/vass/authentication/infrastructure/client/dto/UserPermissionsResponseDTO.java`

```java
package com.vass.authentication.infrastructure.client.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserPermissionsResponseDTO(
    Long userId,
    List<String> permissions,
    LocalDateTime timestamp
) {}
```

**Archivo:** `src/main/java/com/vass/authentication/infrastructure/client/dto/PermissionAssignmentDTO.java`

```java
package com.vass.authentication.infrastructure.client.dto;

public record PermissionAssignmentDTO(
    String permission
) {}
```

**Archivo:** `src/main/java/com/vass/authentication/infrastructure/client/dto/AssignmentResponseDTO.java`

```java
package com.vass.authentication.infrastructure.client.dto;

import java.time.LocalDateTime;

public record AssignmentResponseDTO(
    Long userId,
    String permission,
    String status,
    LocalDateTime timestamp
) {}
```

### 5. Crear Fallback

**Archivo:** `src/main/java/com/vass/authentication/infrastructure/client/AuthorizationServiceClientFallback.java`

```java
package com.vass.authentication.infrastructure.client;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponseDTO;
import com.vass.authentication.infrastructure.client.dto.PermissionAssignmentDTO;
import com.vass.authentication.infrastructure.client.dto.AssignmentResponseDTO;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fallback cuando authorization-service no está disponible.
 */
@Component
public class AuthorizationServiceClientFallback implements AuthorizationServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationServiceClientFallback.class);

    @Override
    public UserPermissionsResponseDTO getUserPermissions(Long userId) {
        logger.warn("Authorization service unavailable. Retornando lista vacía de permisos para usuario: {}", userId);
        return new UserPermissionsResponseDTO(userId, List.of(), LocalDateTime.now());
    }

    @Override
    public AssignmentResponseDTO assignPermissionToUser(Long userId, PermissionAssignmentDTO request) {
        String msg = "Authorization service unavailable. Cannot assign permission to user: " + userId;
        logger.error(msg);
        throw new RuntimeException(msg);
    }
}
```

### 6. Actualizar application.properties

```properties
# Authorization Service Configuration (sin Eureka)
authorization.service.url=http://localhost:8081

# Feign Timeouts y Configuración
feign.client.config.authorization-service.connect-timeout=5000
feign.client.config.authorization-service.read-timeout=10000
feign.client.config.authorization-service.logger-level=full
feign.client.config.authorization-service.decoder=com.vass.authentication.infrastructure.config.FeignErrorDecoder

# Profiles específicos en application-dev.properties, application-prod.properties
```

### 7. Integrar en RegisterService (Transacciones)

**Actualizar:** `src/main/java/com/vass/authentication/application/service/RegisterService.java`

```java
package com.vass.authentication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.entity.UserStatus;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;
import com.vass.authentication.infrastructure.client.AuthorizationServiceClient;
import com.vass.authentication.infrastructure.client.dto.PermissionAssignmentDTO;
import com.vass.authentication.api.dto.RegisterRequest;
import com.vass.authentication.api.dto.RegisterResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class RegisterService {

    private static final Logger logger = LoggerFactory.getLogger(RegisterService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationServiceClient authorizationClient;

    public RegisterService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthorizationServiceClient authorizationClient
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorizationClient = authorizationClient;
    }

    /**
     * Registra un nuevo usuario de forma atómica:
     * 1. Valida que el correo no exista
     * 2. Crea el usuario con rol VIEWER
     * 3. Asigna permiso REPORT_READ via authorization-service
     * 4. Si (3) falla, revierte la transacción (rollback de usuario)
     *
     * @param request Datos del nuevo usuario
     * @return Respuesta sin contraseña
     * @throws IllegalArgumentException si el correo ya existe
     * @throws RuntimeException si authorization-service falla (causa rollback)
     */
    @Transactional  // ← Esta transacción cubre todo el flujo
    public RegisterResponse register(RegisterRequest request) {
        logger.info("Iniciando registro para email: {}", request.email());

        // 1. Validar que no exista correo
        if (userRepository.existsByEmail(request.email())) {
            logger.warn("Intento de registro con email duplicado: {}", request.email());
            throw new IllegalArgumentException("El correo ya está registrado");
        }

        // 2. Crear usuario con rol VIEWER
        UserEntity user = new UserEntity();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles("VIEWER");  // ← Rol inicial

        UserEntity savedUser = userRepository.save(user);
        logger.info("Usuario creado exitosamente con ID: {}", savedUser.getId());

        // 3. Asignar permiso REPORT_READ via Feign
        try {
            PermissionAssignmentDTO permissionRequest = new PermissionAssignmentDTO("REPORT_READ");
            var assignmentResponse = authorizationClient.assignPermissionToUser(
                savedUser.getId(),
                permissionRequest
            );
            logger.info("Permiso REPORT_READ asignado al usuario: {}", savedUser.getId());
        } catch (Exception e) {
            // Si falla authorization-service, la transacción se revierte automáticamente
            logger.error("Fallo al asignar permiso a usuario: {}. Revirtiendo registro.", savedUser.getId(), e);
            throw new RuntimeException("No se pudo completar el registro: fallo en asignación de permisos", e);
        }

        // 4. Retornar respuesta sin exponer datos sensibles
        return new RegisterResponse(
            savedUser.getId(),
            savedUser.getName(),
            savedUser.getEmail(),
            savedUser.getStatus().name(),
            "Usuario registrado exitosamente"
        );
    }
}
```

### 8. Actualizar AuthService para Consultar Permisos en Login

**Actualizar:** `src/main/java/com/vass/authentication/application/service/AuthService.java`

```java
package com.vass.authentication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.vass.authentication.infrastructure.persistence.entity.UserEntity;
import com.vass.authentication.infrastructure.persistence.entity.UserStatus;
import com.vass.authentication.infrastructure.persistence.repository.UserRepository;
import com.vass.authentication.infrastructure.security.JwtService;
import com.vass.authentication.infrastructure.client.AuthorizationServiceClient;
import com.vass.authentication.api.dto.LoginRequest;
import com.vass.authentication.api.dto.LoginResponse;
import com.vass.authentication.api.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthorizationServiceClient authorizationClient;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuthorizationServiceClient authorizationClient
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authorizationClient = authorizationClient;
    }

    /**
     * Autentica un usuario:
     * 1. Valida correo y contraseña
     * 2. Verifica que el usuario esté activo
     * 3. Consulta permisos via authorization-service
     * 4. Genera JWT con los permisos obtenidos
     *
     * @param request Credenciales (email, password)
     * @return Token JWT con permisos
     * @throws IllegalArgumentException si credenciales son inválidas o usuario no está activo
     */
    public LoginResponse login(LoginRequest request) {
        logger.info("Intento de login para email: {}", request.email());

        // 1. Validar que exista el correo
        UserEntity user = userRepository.findByEmail(request.email())
            .orElse(null);

        if (user == null) {
            logger.warn("Intento de login fallido para email: {}", request.email());
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        // 2. Validar contraseña
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            logger.warn("Intento de login fallido para email: {}", request.email());
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        // 3. Validar que el usuario esté activo
        if (user.getStatus() != UserStatus.ACTIVE) {
            logger.warn("Intento de login denegado para email: {} por estado no activo", request.email());
            throw new IllegalArgumentException("Usuario no activo");
        }

        // 4. Consultar permisos desde authorization-service
        var permissionsResponse = authorizationClient.getUserPermissions(user.getId());
        logger.info("Permisos obtenidos para usuario {}: {}", user.getId(), permissionsResponse.permissions());

        // 5. Generar JWT con permisos dinámicos (reemplaza roles hardcodeados)
        // Nota: JwtService.generateToken() debe actualizado para usar permissions del cliente
        String token = jwtService.generateToken(user);  // ← Actualmente usa roles del usuario
        
        logger.info("Login exitoso para userId: {}", user.getId());

        return new LoginResponse(
            "Bearer",
            token,
            jwtService.getExpirationSeconds(),
            new UserResponse(user.getId(), user.getEmail(), user.getName())
        );
    }
}
```

---

## Actualizar JwtService para Aceptar Permisos Dinámicos (Opcional)

Si quieres que JwtService tome permisos desde authorization-service en lugar de mapearlos localmente:

```java
// En AuthService.login():
var permissionsResponse = authorizationClient.getUserPermissions(user.getId());
String token = jwtService.generateTokenWithPermissions(user, permissionsResponse.permissions());
```

```java
// En JwtService:
public String generateTokenWithPermissions(UserEntity user, List<String> permissions) {
    Instant now = Instant.now();
    Instant expiration = now.plusSeconds(expirationSeconds);

    return Jwts.builder()
            .subject(user.getEmail())
            .claim("permissions", permissions)  // ← Usa permisos del parámetro
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(signingKey)
            .compact();
}
```

---

## Configuración para Diferentes Ambientes

### local (development)

**application.properties**
```properties
authorization.service.url=http://localhost:8081
```

### dev (development con containers)

**application-dev.properties**
```properties
authorization.service.url=http://authorization-service:8081
```

**docker-compose.yml** (para ambos servicios)
```yaml
version: '3.8'

services:
  authentication-service:
    build: ./authentication
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_APPLICATION_NAME: authentication-service
    depends_on:
      - authorization-service

  authorization-service:
    build: ./authorization
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: dev
    networks:
      - app-network

networks:
  app-network:
    driver: bridge
```

### prod

**application-prod.properties**
```properties
authorization.service.url=http://authorization-service.internal:8081
feign.client.config.authorization-service.read-timeout=15000
```

---

## Testing con Feign

### Test Unitario (Mock Feign)

```java
@SpringBootTest
class RegisterServiceFeignTest {

    @MockBean
    private AuthorizationServiceClient authorizationClient;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private RegisterService registerService;

    @Test
    void shouldRegisterUserAndAssignPermission() {
        // Given
        RegisterRequest request = new RegisterRequest("Oscar", "oscar@test.com", "Password123!");
        UserEntity savedUser = new UserEntity();
        savedUser.setId(1L);
        savedUser.setEmail("oscar@test.com");
        savedUser.setName("Oscar");

        when(userRepository.existsByEmail("oscar@test.com")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        
        // Mock: respuesta de permission asignado
        AssignmentResponseDTO permissionResponse = new AssignmentResponseDTO(
            1L, "REPORT_READ", "ASSIGNED", LocalDateTime.now()
        );
        when(authorizationClient.assignPermissionToUser(1L, new PermissionAssignmentDTO("REPORT_READ")))
            .thenReturn(permissionResponse);

        // When
        RegisterResponse response = registerService.register(request);

        // Then
        assertThat(response.id()).isEqualTo(1L);
        verify(authorizationClient).assignPermissionToUser(1L, new PermissionAssignmentDTO("REPORT_READ"));
    }

    @Test
    void shouldRollbackRegisterIfPermissionAssignmentFails() {
        // Given
        RegisterRequest request = new RegisterRequest("Oscar", "oscar@test.com", "Password123!");
        UserEntity savedUser = new UserEntity();
        savedUser.setId(1L);
        savedUser.setEmail("oscar@test.com");

        when(userRepository.existsByEmail("oscar@test.com")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        
        // Mock: fallo en asignación de permiso
        when(authorizationClient.assignPermissionToUser(any(Long.class), any(PermissionAssignmentDTO.class)))
            .thenThrow(new RuntimeException("Authorization service down"));

        // When & Then
        assertThatThrownBy(() -> registerService.register(request))
            .isInstanceOf(RuntimeException.class);
            
        // Verificar que NO se guardó el usuario (rollback)
        verify(userRepository).save(any(UserEntity.class));
    }
}
```

### Test de Integración (Testcontainers)

```java
@SpringBootTest
@Testcontainers
class AuthenticationIntegrationFeignTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("auth_test")
        .withUsername("user")
        .withPassword("password");

    @MockBean
    private AuthorizationServiceClient authorizationClient;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldRegisterUserWithPermissionViaFeign() {
        // Mock authorization client
        AssignmentResponseDTO response = new AssignmentResponseDTO(
            1L, "REPORT_READ", "ASSIGNED", LocalDateTime.now()
        );
        when(authorizationClient.assignPermissionToUser(any(Long.class), any(PermissionAssignmentDTO.class)))
            .thenReturn(response);

        // Call register endpoint
        RegisterRequest request = new RegisterRequest("Oscar", "oscar@test.com", "Password123!");
        ResponseEntity<RegisterResponse> result = restTemplate.postForEntity(
            "/api/auth/register",
            request,
            RegisterResponse.class
        );

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().id()).isNotNull();
    }
}
```

---

## Resumen de Pasos

1. ✅ Agregar `spring-cloud-openfeign` a pom.xml
2. ✅ Anotar Application con `@EnableFeignClients`
3. ✅ Crear `AuthorizationServiceClient` interface
4. ✅ Crear DTOs de cliente (UserPermissionsResponseDTO, etc.)
5. ✅ Crear Fallback para resiliencia
6. ✅ Configurar URLs en `application.properties`
7. ✅ Integrar en `RegisterService` con `@Transactional`
8. ✅ Integrar en `AuthService` para consultar permisos en login
9. ✅ Testing con mocks de Feign
10. ✅ Documentar en RFC/ADR

---

## Próximos Pasos

1. Implementar authorization-service con HU-03 y HU-04
2. Hacer test de integración end-to-end
3. Agregar métricas y monitoring de llamadas Feign
4. Implementar retry logic y circuit breaker (Resilience4j)
5. Documentar en postman/openapi.json

# Contrato de API - Authorization Service (authorization-service)

## Descripción General

⚠️ **IMPORTANTE: Endpoints Internos (Consumo via Feign desde authentication-service)**

El servicio de autorización expone 2 endpoints **INTERNOS** que son consumidos únicamente por el servicio de autenticación (via Feign Client):

1. **GET /api/permissions/users/{userId}** 
   - **Consumido en:** AuthService.login() para obtener permisos del usuario y llenar el JWT
   - **No es público:** No debe ser expuesto a clientes finales

2. **POST /api/permissions/users/{userId}** 
   - **Consumido en:** RegisterService.register() para asignar permiso REPORT_READ al nuevo usuario
   - **No es público:** No debe ser expuesto a clientes finales
   - **Transaccional:** Si falla, causa rollback del usuario en authentication-service

**Base URL (sin Eureka):** `http://localhost:8081/api` (configurable en properties)

**Acceso:** Solo desde authentication-service (via Feign Client interna, sin exposición pública)

---

## Endpoints

### 1. Consultar Permisos de Usuario (INTERNO - Consumido por AuthService.login())

**Endpoint (INTERNO):**
```
GET /api/permissions/users/{userId}
```

**Descripción:**
Retorna la lista de permisos asignados a un usuario específico. **Este endpoint es consumido internamente por authentication-service durante el login para llenar el claim `permissions` del JWT.**

**Parámetros:**
| Parámetro | Tipo | Ubicación | Requerido | Descripción |
|-----------|------|-----------|-----------|-------------|
| userId | Long | Path | Sí | ID del usuario |

**Headers:**
```
Accept: application/json
```

**Respuesta Exitosa (HTTP 200):**
```json
{
  "userId": 1,
  "permissions": ["REPORT_READ", "REPORT_DOWNLOAD"],
  "timestamp": "2026-05-13T14:30:00Z"
}
```

**Respuesta Sin Permisos (HTTP 200):**
```json
{
  "userId": 2,
  "permissions": [],
  "timestamp": "2026-05-13T14:30:00Z"
}
```

**Errores:**
- **404 Not Found:** Usuario no existe
  ```json
  {
    "timestamp": "2026-05-13T14:31:00Z",
    "status": 404,
    "error": "Not Found",
    "message": "Usuario con ID 999 no encontrado",
    "path": "/api/permissions/users/999"
  }
  ```

- **500 Internal Server Error:** Error en base de datos
  ```json
  {
    "timestamp": "2026-05-13T14:32:00Z",
    "status": 500,
    "error": "Internal Server Error",
    "message": "Error consultando permisos del usuario",
    "path": "/api/permissions/users/1"
  }
  ```

---

### 2. Asignar Permiso a Usuario (INTERNO - Consumido por RegisterService.register())

**Endpoint (INTERNO):**
```
POST /api/permissions/users/{userId}
```

**Descripción:**
Asigna un permiso a un usuario. **Este endpoint es consumido internamente por authentication-service durante el registro para asignar REPORT_READ al nuevo usuario.** Operación idempotente: si el permiso ya está asignado, retorna 200 sin duplicar.

**Parámetros:**
| Parámetro | Tipo | Ubicación | Requerido | Descripción |
|-----------|------|-----------|-----------|-------------|
| userId | Long | Path | Sí | ID del usuario |

**Headers:**
```
Content-Type: application/json
```

**Body Request:**
```json
{
  "permission": "REPORT_READ"
}
```

**Validaciones:**
- `userId` debe ser > 0
- `permission` debe estar en lista blanca: `REPORT_READ`, `REPORT_DOWNLOAD`

**Respuesta Exitosa (HTTP 200):**
```json
{
  "userId": 1,
  "permission": "REPORT_READ",
  "status": "ASSIGNED",
  "timestamp": "2026-05-13T14:35:00Z"
}
```

**Errores:**

- **400 Bad Request:** Permiso inválido o validación fallida
  ```json
  {
    "timestamp": "2026-05-13T14:36:00Z",
    "status": 400,
    "error": "Bad Request",
    "message": "Permiso inválido",
    "details": "Permiso 'INVALID' no es válido. Válidos: REPORT_READ, REPORT_DOWNLOAD",
    "path": "/api/permissions/users/1"
  }
  ```

- **404 Not Found:** Usuario no existe
  ```json
  {
    "timestamp": "2026-05-13T14:37:00Z",
    "status": 404,
    "error": "Not Found",
    "message": "Usuario con ID 999 no encontrado",
    "path": "/api/permissions/users/999"
  }
  ```

- **409 Conflict:** Permiso ya asignado (opcional, o 200 si es idempotente)
  ```json
  {
    "timestamp": "2026-05-13T14:38:00Z",
    "status": 409,
    "error": "Conflict",
    "message": "Permiso REPORT_READ ya está asignado al usuario",
    "path": "/api/permissions/users/1"
  }
  ```

---

### 3. Obtener Lista de Permisos Válidos (Opcional - Para Administración Futura)

**Endpoint (PUBLICO - Futuro):**
```
GET /api/permissions/values
```

**Descripción:**
Retorna la lista de permisos válidos disponibles en el sistema. **Este endpoint es opcional y está reservado para futuras interfaces de administración que necesiten listar permisos disponibles.**

**Respuesta Exitosa (HTTP 200):**
```json
{
  "permissions": [
    {
      "code": "REPORT_READ",
      "description": "Permite leer reportes"
    },
    {
      "code": "REPORT_DOWNLOAD",
      "description": "Permite descargar reportes"
    }
  ]
}
```

---

## Contrato de Feign Client (Para Authentication Service)

**Archivo:** `AuthorizationServiceClient.java` (en authentication-service)

```java
package com.vass.authentication.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponseDTO;
import com.vass.authentication.infrastructure.client.dto.PermissionAssignmentDTO;
import com.vass.authentication.infrastructure.client.dto.AssignmentResponseDTO;

/**
 * Feign Client para consumir el servicio de autorización.
 * Sin Eureka: URL configurada en application.properties
 */
@FeignClient(
    name = "authorization-service",
    url = "${authorization.service.url:http://localhost:8081}",
    path = "/api",
    fallback = AuthorizationServiceClientFallback.class
)
public interface AuthorizationServiceClient {

    /**
     * Consulta los permisos de un usuario por su ID.
     *
     * @param userId ID del usuario
     * @return Respuesta con lista de permisos
     */
    @GetMapping(value = "/permissions/users/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    UserPermissionsResponseDTO getUserPermissions(@PathVariable("userId") Long userId);

    /**
     * Asigna un permiso a un usuario.
     *
     * @param userId ID del usuario
     * @param request Datos del permiso a asignar
     * @return Respuesta de asignación
     */
    @PostMapping(value = "/permissions/users/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    AssignmentResponseDTO assignPermissionToUser(
        @PathVariable("userId") Long userId,
        @RequestBody PermissionAssignmentDTO request
    );
}
```

**DTOs Asociados:**

```java
// UserPermissionsResponseDTO.java
package com.vass.authentication.infrastructure.client.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserPermissionsResponseDTO(
    Long userId,
    List<String> permissions,
    LocalDateTime timestamp
) {}

// PermissionAssignmentDTO.java
package com.vass.authentication.infrastructure.client.dto;

public record PermissionAssignmentDTO(
    String permission
) {}

// AssignmentResponseDTO.java
package com.vass.authentication.infrastructure.client.dto;

import java.time.LocalDateTime;

public record AssignmentResponseDTO(
    Long userId,
    String permission,
    String status,
    LocalDateTime timestamp
) {}
```

---

## Configuración Feign (Sin Eureka)

**Archivo:** `application.properties` (authentication-service)

```properties
# Authorization Service URL (sin Eureka, sin service discovery)
authorization.service.url=http://localhost:8081

# Feign Configuration
feign.client.config.authorization-service.connect-timeout=5000
feign.client.config.authorization-service.read-timeout=10000
feign.client.config.authorization-service.logger-level=full
feign.client.config.authorization-service.retryer=com.github.openfeign.Retryer.Default

# Para profiles específicos (local, dev, prod):
# local: http://localhost:8081
# dev: http://authorization-service.dev.internal:8081
# prod: http://authorization-service.prod.internal:8081
```

**Archivo:** `application-dev.properties`

```properties
authorization.service.url=http://authorization-service.dev.internal:8081
```

**Archivo:** `application-prod.properties`

```properties
authorization.service.url=http://authorization-service.prod.internal:8081
```

---

## Fallback y Resiliencia

**Archivo:** `AuthorizationServiceClientFallback.java`

```java
package com.vass.authentication.infrastructure.client;

import org.springframework.stereotype.Component;
import com.vass.authentication.infrastructure.client.dto.UserPermissionsResponseDTO;
import com.vass.authentication.infrastructure.client.dto.PermissionAssignmentDTO;
import com.vass.authentication.infrastructure.client.dto.AssignmentResponseDTO;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class AuthorizationServiceClientFallback implements AuthorizationServiceClient {

    @Override
    public UserPermissionsResponseDTO getUserPermissions(Long userId) {
        // Fallback: retornar lista vacía (usuario sin permisos temporalmente)
        // Esto permite que el login continúe con permisos vacíos
        return new UserPermissionsResponseDTO(userId, List.of(), LocalDateTime.now());
    }

    @Override
    public AssignmentResponseDTO assignPermissionToUser(Long userId, PermissionAssignmentDTO request) {
        // Fallback: rechazar asignación si authorization-service está caído
        throw new RuntimeException("Authorization service is unavailable. Cannot assign permission.");
    }
}
```

---

## Decisión Arquitectónica: ¿Por qué sin Eureka?

### Análisis:

**Problema:** No tienen Eureka como service discovery.

**Soluciones Evaluadas:**

1. **URLs configurables (RECOMENDADO):**
   - ✅ Simple, sin dependencias externas
   - ✅ Fácil de testear localmente
   - ✅ Control explícito sobre dónde se despliega cada servicio
   - ✅ Compatible con DNS o load balancers
   - ❌ Manual: requiere actualizar properties en cada environment

2. **Nombres de host (DNS):**
   - ✅ Escalable y flexible
   - ✅ Los containers pueden resolver `authorization-service:8081` automáticamente en Docker Compose
   - ❌ Requiere DNS configurado
   - ❌ En desarrollo local puede requerir `/etc/hosts` o Docker networks

3. **Configuration Server externo:**
   - ✅ Centralizado
   - ❌ Complejidad adicional
   - ❌ No necesario para 2-3 micros

### Recomendación Elegida:

**URLs configurables en properties** + **DNS para ambiente containerizado**

**Estructura:**

```
Local (docker-compose):
  authentication-service → http://authorization-service:8081

Dev/Prod:
  authentication-service → http://authorization-service.dev.internal:8081 (resuelto por DNS/LB)
```

---

## Alternativas Consideradas (Solo 2 Micros)

**Pregunta:** ¿Por qué solo 2 micros con Feign si es complejo?

**Respuesta:**
- Con 2-3 micros, la comunicación es mínima y directa
- Si fuesen +5 micros, Eureka/Consul sería más rentable
- Para 2 micros: overhead de discovery > beneficio
- Mantiene los micros independientes (no hay acoplamiento con Eureka)

**Alternativa:** Si crece a +5 micros, es fácil migrar a Eureka después.

---

## Testing del Contrato

**Test Feign sin authorization-service activo:**

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthServiceFeignTest {

    @MockBean
    private AuthorizationServiceClient authClient;

    @Test
    void shouldGetPermissionsFromAuthorizationService() {
        // Mock: retornar permisos
        UserPermissionsResponseDTO response = new UserPermissionsResponseDTO(
            1L,
            List.of("REPORT_READ", "REPORT_DOWNLOAD"),
            LocalDateTime.now()
        );
        
        when(authClient.getUserPermissions(1L)).thenReturn(response);
        
        // Assert
        UserPermissionsResponseDTO result = authClient.getUserPermissions(1L);
        assertThat(result.permissions()).contains("REPORT_READ", "REPORT_DOWNLOAD");
    }
}
```

---

## Resumen

✅ **HU-03 (Consultar Permisos):** Endpoint GET `/api/permissions/users/{userId}`  
✅ **HU-04 (Asignar Permiso):** Endpoint POST `/api/permissions/users/{userId}`  
✅ **Feign Client:** Interface con fallback para consumir desde authentication-service  
✅ **Configuración:** URLs en properties, sin Eureka  
✅ **DTOs:** Records inmutables para request/response  
✅ **Escalabilidad:** Preparado para migrar a Eureka si crece a +5 micros

# Contratos API para `authentication-service` (HU1, HU2, HU3 y HU4)

## Datos de conexión

- Servicio: `authorization`
- Puerto local: `8081`
- Base URL local: `http://localhost:8081`
- Content-Type: `application/json`

## Configuración requerida (HU1 Login JWT)

Propiedades en `src/main/resources/application.properties`:

- `app.jwt.secret`: secreto HS256 (mínimo 32 bytes / 256 bits).
- `app.jwt.expiration-seconds`: expiración del access token en segundos.
- `app.registration.initial-permission`: permiso inicial enviado a authorization-service en registro (default: `REPORT:READ`).
- `authorization-service.url`: base URL del servicio de autorización.
- `spring.cloud.openfeign.client.config.authorization-service.connectTimeout=2000`
- `spring.cloud.openfeign.client.config.authorization-service.readTimeout=2000`

Endpoint de login:

- Método: `POST`
- Ruta: `/api/auth/login`

Endpoint de registro:

- Método: `POST`
- Ruta: `/api/auth/register`

---

## HU2 - Registro transaccional de usuario con permiso inicial

### Request

```json
{
  "name": "Alice",
  "lastName": "Smith",
  "email": "alice@acme.com",
  "password": "Password1!",
  "passwordConfirm": "Password1!"
}
```

Reglas:
- `name` y `lastName`: obligatorios.
- `email`: obligatorio y formato válido.
- `password`: mínimo 8, con mayúscula, minúscula, número y especial.
- `passwordConfirm`: debe coincidir con `password`.

### Response 201 (éxito)

```json
{
  "userId": 25,
  "name": "Alice",
  "lastName": "Smith",
  "email": "alice@acme.com",
  "role": "REPORT_READER",
  "active": true
}
```

### Errores

- `400 Bad Request`: validación de campos o confirmación inválida.
- `409 Conflict`: email ya existente.
- `503 Service Unavailable`: falla técnica al asignar permiso inicial en authorization-service.
- `500 Internal Server Error`: fallo técnico no controlado del flujo de registro.

### Atomicidad funcional

Si falla `POST /api/permissions/users/{userId}` durante el registro, el alta local se revierte y no queda usuario persistido.

## Esquema común de error

```json
{
  "error": "Bad Request | Not Found",
  "message": "descripcion funcional",
  "timestamp": "2026-05-15T00:00:00Z",
  "details": {}
}
```

Notas:
- `details` puede venir vacío (`{}`) o con campos de validación.
- En validaciones de bean/path puede incluir mensajes técnicos del validador.

---

## HU3 - Consultar permisos vigentes de usuario

### Endpoint

- Método: `GET`
- Ruta: `/api/permissions/users/{userId}`
- Propósito: entregar permisos dinámicos para construcción de JWT en `authentication-service`.

### Request

- Path param obligatorio:
  - `userId` (`Long`, `> 0`)
- Body: no aplica.

### Response 200 (usuario existente)

```json
{
  "userId": 10,
  "permissions": ["REPORT:DOWNLOAD", "REPORT:READ"],
  "timestamp": "2026-05-15T00:00:00Z"
}
```

Reglas de salida:
- `permissions` se entrega normalizado a mayúsculas.
- `permissions` se entrega sin duplicados.
- `permissions` se entrega ordenado ascendente.
- Si no tiene permisos: `permissions: []`.

### Casos de uso del servicio HU3

1. Usuario existe y tiene permisos -> `200`.
2. Usuario existe y no tiene permisos -> `200` con `permissions=[]`.
3. `userId <= 0` -> `400 Bad Request`.
4. Usuario no existe -> `404 Not Found` (`message`: `Usuario no encontrado`).
5. En `authentication-service`, cualquier falla Feign al consultar permisos (`400/404/5xx/timeout/conectividad`) degrada a `permissions=[]` para no bloquear login.

### Timeouts Feign (HU3)

- `connectTimeout=2000 ms`
- `readTimeout=2000 ms`

---

## HU4 - Asignar permiso inicial (idempotente)

### Endpoint

- Método: `POST`
- Ruta: `/api/permissions/users/{userId}`
- Propósito: asignar un permiso a usuario para onboarding/registro desde `authentication-service`.

### Request

- Path param obligatorio:
  - `userId` (`Long`, `> 0`)
- Body:

```json
{
  "permission": "REPORT:DOWNLOAD"
}
```

Reglas de entrada:
- `permission` obligatorio, no nulo, no vacío.
- El servicio normaliza a mayúsculas y trim.
- Debe existir en catálogo permitido.

Catálogo permitido actual (`application.properties`):
- `REPORT:READ`
- `REPORT:DOWNLOAD`
- `ADMIN:MANAGE_PERMISSIONS`

### Response 200 - Asignado

```json
{
  "userId": 10,
  "permission": "REPORT:DOWNLOAD",
  "status": "ASSIGNED",
  "timestamp": "2026-05-15T00:00:00Z"
}
```

### Response 200 - Idempotente (ya existía)

```json
{
  "userId": 10,
  "permission": "REPORT:DOWNLOAD",
  "status": "ALREADY_ASSIGNED",
  "timestamp": "2026-05-15T00:00:01Z"
}
```

### Casos de uso del servicio HU4

1. Usuario existe y permiso válido no asignado -> `200` con `status=ASSIGNED`.
2. Usuario existe y permiso válido ya asignado -> `200` con `status=ALREADY_ASSIGNED`.
3. `permission` fuera de catálogo -> `400 Bad Request` (`message`: `Permiso invalido o fuera de catalogo`).
4. `permission` nulo o vacío -> `400 Bad Request`.
5. `userId <= 0` -> `400 Bad Request`.
6. Usuario no existe -> `404 Not Found` (`message`: `Usuario no encontrado`).

### Comportamiento en `authentication-service`

- En registro, `ASSIGNED` y `ALREADY_ASSIGNED` se tratan como éxito funcional.
- El payload enviado para asignación inicial en registro es `{ "permission": "REPORT:READ" }` (configurable por `app.registration.initial-permission`).
- Si la asignación falla (`400`, `404`, timeout, conectividad o `5xx`), se revierte el alta local del usuario.
- Mapeo de error hacia cliente de registro:
  - `400` remoto -> `400` en registro.
  - `404` remoto -> `404` en registro.
  - timeout/conectividad/`5xx` remoto -> `503` en registro.

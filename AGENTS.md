# AGENTS.md

## Contexto del Proyecto

- **Nombre**: `authentication-service`
- **Tipo**: API REST (Spring Boot)
- **Lenguaje**: Java 17
- **Objetivo**: autenticar usuarios con JWT y delegar autorización dinámica a `authorization-service`.
- **Arquitectura objetivo**: capas desacopladas (controller, application/service, infraestructura/integraciones).

## Alcance Funcional Consolidado (Historias 1-4)

Este archivo adapta y consolida:

- `historias/historia1.md` (login JWT con permisos dinámicos)
- `historias/historia2.md` (registro de usuario con permiso inicial)
- `historias/historia3.md` (consulta dinámica de permisos)
- `historias/historia4.md` (asignación de permiso predeterminado)

## Historias de Usuario Objetivo

### HU-01: Login con JWT y permisos dinámicos

**Endpoint**: `POST /api/auth/login`

**Reglas clave**:
- Validar formato/obligatoriedad de email y password.
- Verificar usuario existente, activo y password con BCrypt.
- Consultar permisos en `authorization-service` vía Feign.
- Emitir JWT con `sub`, `email`, `permissions`, `iat`, `exp`.
- Si falla Feign por timeout/indisponibilidad: continuar con `permissions=[]`.

**Respuestas esperadas**:
- `200 OK`: `tokenType`, `accessToken`, `expiresIn`, `userId`, `username`.
- `400 Bad Request`: errores de formato/validación.
- `401 Unauthorized`: credenciales inválidas o usuario inactivo (mensaje genérico).

### HU-02: Registro de usuario con consistencia transaccional

**Endpoint**: `POST /api/auth/register`

**Reglas clave**:
- Validar datos de entrada, política de password y `passwordConfirm`.
- Rechazar email duplicado.
- Crear usuario activo con rol inicial `REPORT_READER`.
- Guardar password únicamente como hash BCrypt.
- Asignar permiso inicial mediante Feign.
- La operación debe ser atómica: si falla la asignación remota, rollback del alta.

**Respuestas esperadas**:
- `201 Created`: datos del usuario sin credenciales.
- `400 Bad Request`: validaciones de entrada.
- `409 Conflict`: email ya existe.
- `500/503`: error técnico de integración según política.

### HU-03: Consulta de permisos en `authorization-service`

**Endpoint remoto**: `GET /api/permissions/users/{userId}`

**Reglas clave**:
- Integración obligatoria vía OpenFeign.
- `userId` válido: `Long > 0`.
- Permisos normalizados en mayúsculas, sin duplicados y ordenados.
- Si el usuario no existe: contrato remoto `404`.
- En errores de conectividad/timeout: fallback local a `permissions=[]` en login.

**Timeouts requeridos**:
- `connectTimeout = 2000 ms`
- `readTimeout = 2000 ms`

### HU-04: Asignación de permiso inicial e idempotencia

**Endpoint remoto**: `POST /api/permissions/users/{userId}`

**Body requerido**:
```json
{ "permission": "REPORT:READ" }
```

**Reglas clave**:
- Validar `userId` y `permission`.
- Soportar idempotencia remota:
  - `status=ASSIGNED` si es nueva asignación.
  - `status=ALREADY_ASSIGNED` si ya existía.
- Si falla asignación durante el registro, revertir usuario en auth.

**Catálogo permitido (actual)**:
- `REPORT:READ`
- `REPORT:DOWNLOAD`
- `ADMIN:MANAGE_PERMISSIONS`

## Reglas de Negocio Obligatorias

1. El login nunca debe exponer credenciales ni hashes en respuesta o logs.
2. La autenticación no debe bloquearse por caídas de autorización: fallback a permisos vacíos.
3. El registro debe mantener consistencia: no dejar usuario persistido si falla asignación inicial.
4. Todas las validaciones funcionales deben mapearse a códigos HTTP consistentes.
5. La contraseña siempre se persiste con BCrypt.

## Requisitos No Funcionales

- **Seguridad**
  - JWT firmado con HS256 y secreto mínimo de 256 bits.
  - Sanitización de logs y manejo de errores sin fuga de datos sensibles.
- **Resiliencia**
  - Integración Feign con timeout corto y fallback controlado.
- **Observabilidad**
  - Registrar éxito/fallo con trazabilidad por `userId`.
  - Medir tasa de fallback de permisos.
- **Persistencia local**
  - H2 en ambientes de desarrollo/pruebas de esta iteración.

## Estándares de Testing

### Cobertura mínima por historia

- **HU-01**
  - Login válido con permisos.
  - Login válido con fallback (`permissions=[]`).
  - Credenciales inválidas / usuario inactivo.
- **HU-02**
  - Registro exitoso.
  - Email duplicado.
  - Password inválida o confirmación no coincide.
  - Rollback por fallo Feign.
- **HU-03**
  - Integración Feign para `200`, `400`, `404`, timeout.
  - Normalización y deduplicación de permisos.
- **HU-04**
  - Asignación `ASSIGNED` y `ALREADY_ASSIGNED`.
  - Error de validación y fallback de flujo transaccional.
  - Concurrencia sin duplicados.

### Tipos de pruebas

- Unitarias (servicios, validadores, mapeos, manejo de excepciones).
- Integración (H2 + clientes Feign mockeados/simulados).
- E2E del flujo `register -> login -> consumo de endpoint protegido`.

## Contratos y Componentes Esperados

### Endpoints de `authentication-service`

- `POST /api/auth/login`
- `POST /api/auth/register`

### Integraciones internas (Feign)

- `GET /api/permissions/users/{userId}`
- `POST /api/permissions/users/{userId}`

### Componentes de referencia

- `AuthenticationController`
- `AuthenticationService` / `LoginService`
- `UserRegistrationService`
- `PasswordValidator`
- `JwtService` / `JwtTokenProvider`
- `AuthorizationServiceClient`
- `UserRepository`, `RoleRepository`

## Definición de Hecho (DoD)

Una implementación se considera completa cuando:

1. Cumple los criterios funcionales de HU-01 a HU-04.
2. Maneja fallas remotas con la política definida (fallback o rollback según caso).
3. No expone datos sensibles.
4. Incluye pruebas unitarias e integración para escenarios felices y de error.
5. Documenta configuración de Feign y JWT en propiedades del proyecto.

---

**Última actualización**: 2026-05-14  
**Fuente funcional**: `historia1.md`, `historia2.md`, `historia3.md`, `historia4.md`

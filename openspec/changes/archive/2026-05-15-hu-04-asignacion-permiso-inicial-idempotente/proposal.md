## Why

El registro de usuarios en `authentication-service` requiere una asignación inicial de permisos confiable para habilitar acceso básico inmediato. Esta capacidad formaliza idempotencia y rollback para evitar inconsistencias entre autenticación y autorización.

## What Changes

- Integrar y estandarizar la llamada Feign a `POST /api/permissions/users/{userId}` durante registro.
- Enviar payload obligatorio `{ "permission": "REPORT:READ" }` para permiso inicial.
- Manejar explícitamente respuestas idempotentes remotas: `ASSIGNED` y `ALREADY_ASSIGNED`.
- Asegurar tratamiento de errores remotos (`400`, `404`, timeout/disponibilidad) con política de rollback en auth.
- Definir reglas de validación y mapeo de errores para consistencia de contrato hacia clientes.
- Reforzar observabilidad del flujo de asignación inicial y de las reversiones transaccionales.

## Capabilities

### New Capabilities

- `auth-initial-permission-assignment-idempotency`: Asignación remota de permiso inicial con manejo idempotente (`ASSIGNED`/`ALREADY_ASSIGNED`) y rollback local si falla la integración durante registro.

### Modified Capabilities

- Ninguna.

## Impact

- Código afectado: `UserRegistrationService`, `AuthorizationServiceClient`, DTOs de asignación, excepciones y mapeo HTTP.
- Dependencias/sistemas: contrato remoto de `authorization-service` para asignación de permisos por usuario.
- Flujo afectado: registro de usuario en auth, incluyendo consistencia transaccional con integración remota.
- Testing: ampliar pruebas unitarias/integración para estados idempotentes, errores de validación remota y rollback.

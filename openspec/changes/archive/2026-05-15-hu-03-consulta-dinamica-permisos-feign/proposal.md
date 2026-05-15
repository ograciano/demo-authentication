## Why

`authentication-service` depende de permisos actualizados para emitir JWT con autorización vigente sin acoplar reglas estáticas. Esta mejora formaliza el contrato de consulta Feign y su manejo resiliente para mantener disponibilidad en login ante fallas de integración.

## What Changes

- Definir la capacidad de consulta de permisos vía OpenFeign a `GET /api/permissions/users/{userId}`.
- Validar precondición de `userId` (`Long > 0`) antes de invocar la integración remota.
- Normalizar permisos de salida (mayúsculas, sin duplicados, orden ascendente) para consumo consistente.
- Asegurar comportamiento ante respuestas remotas `200`, `400`, `404` y errores técnicos.
- Mantener política de fallback en `authentication-service`: si hay timeout/conectividad/`5xx`, degradar a `permissions=[]`.
- Estandarizar timeouts Feign de `connectTimeout=2000ms` y `readTimeout=2000ms`.

## Capabilities

### New Capabilities

- `auth-permissions-feign-query-resilience`: Consulta dinámica de permisos por Feign con validación de entrada, normalización de datos y estrategia de resiliencia para no bloquear autenticación.

### Modified Capabilities

- Ninguna.

## Impact

- Código afectado: `AuthorizationServiceClient`, `PermissionService`, DTOs de respuesta de permisos y configuración Feign.
- Dependencias/sistemas: contrato remoto de `authorization-service` para permisos de usuario.
- Flujo afectado: login consume permisos normalizados y aplica fallback controlado en errores de integración.
- Testing: agregar/ajustar pruebas unitarias e integración para rutas `200`, `400`, `404`, timeout y fallback.

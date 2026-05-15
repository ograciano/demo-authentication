## Why

El servicio de autenticación necesita un flujo de registro que cree cuentas seguras y consistentes con autorización desde el primer momento. Esta capacidad es prioritaria para evitar usuarios huérfanos o sin permisos cuando falla la integración remota.

## What Changes

- Implementar `POST /api/auth/register` con validación de datos personales, email, política de contraseña y confirmación de contraseña.
- Rechazar registros con email duplicado mediante respuesta `409 Conflict`.
- Persistir nuevos usuarios activos con contraseña hasheada usando BCrypt y rol inicial `REPORT_READER`.
- Integrar asignación de permiso inicial vía OpenFeign a `POST /api/permissions/users/{userId}`.
- Garantizar atomicidad de la operación: si falla la asignación remota, revertir el alta local de usuario.
- Estandarizar manejo de errores de validación e integración (`400`, `409`, `500/503`) sin exponer datos sensibles.

## Capabilities

### New Capabilities

- `auth-user-registration-transactional-initial-permission`: Registro de usuario con validaciones de seguridad, alta activa con rol inicial y asignación remota de permiso inicial en flujo transaccional con rollback.

### Modified Capabilities

- Ninguna.

## Impact

- Código afectado: controller de autenticación, servicio de registro, validadores de contraseña, repositorios de usuario/rol, cliente Feign y manejo global de excepciones.
- API afectada: incorporación o ajuste de contrato `POST /api/auth/register`.
- Dependencias/sistemas: `authorization-service` para asignación de permiso inicial y configuración Feign para errores/timeout.
- Persistencia: alta de usuario y rollback transaccional en H2 para entornos de desarrollo/pruebas.
- Testing: nuevas pruebas unitarias/integración para éxito, duplicado, validaciones y fallo remoto con rollback.

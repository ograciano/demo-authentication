## Historia de Usuario Enriquecida

### Contexto y Objetivo de Negocio
Como usuario nuevo, necesito registrarme para acceder al sistema con una cuenta activa y permisos iniciales mínimos, garantizando seguridad en credenciales y consistencia entre autenticación y autorización.

### Descripción Funcional Detallada
El endpoint `POST /api/auth/register` recibe datos básicos del usuario, valida formato de email y política de contraseña, verifica que el correo no exista, crea el usuario con rol `REPORT_READER` y contraseña cifrada con BCrypt, y luego invoca por Feign a `authorization-service` para asignar el permiso inicial. La operación debe ser atómica: si la asignación remota falla, el registro se revierte.

### Criterios de Aceptación
- [ ] Con datos válidos y email no registrado, responde `201 Created` con datos del usuario sin credenciales.
- [ ] Si el email ya existe, responde `409 Conflict`.
- [ ] Si hay errores de validación, responde `400 Bad Request` con detalle de campos.
- [ ] El usuario se crea con estado activo y rol inicial `REPORT_READER`.
- [ ] La contraseña se persiste solo como hash BCrypt.
- [ ] Se asigna permiso inicial mediante Feign en la misma transacción lógica de registro.
- [ ] Si falla la asignación de permiso, no queda usuario persistido.

### Casos Límite y Validaciones
- `password` y `passwordConfirm` no coinciden: `400`.
- Password débil (sin mayúscula/minúscula/número/especial o <8): `400`.
- Nombre/apellido vacíos: `400`.
- Error de red/timeout al asignar permiso: rollback y error controlado (`500`/`503` según política).

### Requisitos No Funcionales Relevantes
- Seguridad: hardening de validaciones y no exposición de hash/token.
- Integridad: atomicidad entre alta de usuario y asignación de permiso.
- Rendimiento: registro estable bajo carga moderada.
- Persistencia: H2 para desarrollo y pruebas automatizadas.
- Observabilidad: trazas de registro y rollback con correlación por `userId`.

### Consideraciones Técnicas (alineadas al proyecto)
- Endpoint: `POST /api/auth/register`.
- Componentes: `AuthenticationController`, `UserRegistrationService`, `PasswordValidator`, `RoleRepository`, `UserRepository`, `AuthorizationServiceClient`.
- Integración interna Feign: `POST /api/permissions/users/{userId}` con permiso inicial.
- Excepciones: `EmailAlreadyExistsException`, `RegistrationFailedException`, `PermissionAssignmentException`.

### Requisitos de Testing (alto nivel)
- Unitarias: validaciones, duplicado de email, hashing, rollback por fallo Feign.
- Integración: persistencia en H2 + mock de autorización.
- E2E: registro completo y login posterior exitoso.

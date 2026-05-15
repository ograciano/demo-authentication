## Historia de Usuario Enriquecida

### Contexto y Objetivo de Negocio
Como usuario registrado, necesito autenticarme con correo y contraseña para obtener un JWT y consumir recursos protegidos. El token debe incluir permisos de negocio vigentes para evitar autorización estática en `authentication-service`.

### Descripción Funcional Detallada
El endpoint `POST /api/auth/login` recibe credenciales, valida formato y obligatoriedad, busca el usuario en base de datos, valida contraseña con BCrypt y verifica estado activo. Si la autenticación es correcta, `authentication-service` consulta permisos dinámicos en `authorization-service` mediante Feign Client y genera un JWT firmado con claims de identidad y permisos. Si el servicio externo falla por timeout o indisponibilidad, el login no debe bloquearse: se emite token con permisos vacíos.

### Criterios de Aceptación
- [ ] Con credenciales válidas de un usuario activo, responde `200 OK` con `tokenType`, `accessToken`, `expiresIn`, `userId` y `username`.
- [ ] Con credenciales inválidas (email inexistente, contraseña incorrecta o usuario inactivo), responde `401 Unauthorized` con mensaje genérico.
- [ ] El JWT incluye `sub` (userId), `email`, `permissions`, `iat` y `exp`.
- [ ] La contraseña no se expone en respuestas ni logs.
- [ ] La consulta de permisos se realiza vía Feign Client a `authorization-service`.
- [ ] Si falla la consulta de permisos, el login continúa con `permissions=[]`.

### Casos Límite y Validaciones
- Email vacío o con formato inválido: `400 Bad Request`.
- Password vacío: `400 Bad Request`.
- Usuario inactivo: `401 Unauthorized`.
- Falla de `authorization-service` (timeout/error): login exitoso con permisos vacíos.
- Token expirado o inválido en consumo posterior: rechazo por capa de seguridad.

### Requisitos No Funcionales Relevantes
- Seguridad: JWT firmado HS256, secreto de al menos 256 bits, BCrypt para contraseñas.
- Rendimiento: latencia de login estable; timeout Feign acotado (2s).
- Observabilidad: logs de éxito/fallo sin datos sensibles, trazabilidad por `userId`.
- Persistencia: uso de H2 para entorno local/pruebas de esta iteración.

### Consideraciones Técnicas (alineadas al proyecto)
- Endpoint: `POST /api/auth/login`.
- Componentes: `AuthenticationController`, `AuthenticationService`, `JwtService`, `UserRepository`, `AuthorizationServiceClient`.
- Integración interna: Feign (`GET /api/permissions/users/{userId}`).
- Política de error: `InvalidCredentialsException` -> `401`.

### Requisitos de Testing (alto nivel)
- Unitarias: credenciales válidas, inválidas, usuario inactivo, fallback de permisos.
- Integración: login contra H2 + mock de Feign.
- E2E: login y uso de JWT en endpoint protegido.

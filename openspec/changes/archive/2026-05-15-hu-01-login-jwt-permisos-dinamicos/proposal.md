## Why

Actualmente el login no garantiza un JWT con permisos dinámicos obtenidos en tiempo real desde `authorization-service`, lo que limita la coherencia entre autenticación y autorización. Esta capacidad es necesaria ahora para habilitar consumo seguro de endpoints protegidos sin acoplar permisos estáticos en `authentication-service`.

## What Changes

- Implementar el endpoint `POST /api/auth/login` con validación de entrada (`email`, `password`) y respuesta estandarizada de autenticación.
- Autenticar contra persistencia local validando usuario existente, estado activo y contraseña con BCrypt.
- Integrar consulta de permisos vía OpenFeign a `GET /api/permissions/users/{userId}` con timeouts cortos.
- Generar JWT HS256 con claims `sub`, `email`, `permissions`, `iat` y `exp`.
- Aplicar política de resiliencia: si falla la consulta remota de permisos (timeout/indisponibilidad), continuar login con `permissions=[]`.
- Asegurar manejo de errores consistente: `400` para validación, `401` genérico para credenciales inválidas/inactivo, sin fuga de datos sensibles.

## Capabilities

### New Capabilities

- `auth-login-jwt-dynamic-permissions`: Login con JWT firmado y permisos dinámicos provenientes de `authorization-service`, con fallback seguro a permisos vacíos ante falla de integración.

### Modified Capabilities

- Ninguna.

## Impact

- Código afectado: controlador y servicio de autenticación, proveedor JWT, cliente Feign de autorización, validaciones y manejo global de excepciones.
- API afectada: alta del contrato `POST /api/auth/login` en `authentication-service`.
- Dependencias/sistemas: `authorization-service` (consulta de permisos), configuración de JWT y Feign timeout.
- Testing: nuevas pruebas unitarias e integración para éxito, credenciales inválidas, usuario inactivo y fallback por falla remota.

## 1. Contrato y validaciones de login

- [x] 1.1 Crear/ajustar DTOs de `POST /api/auth/login` con validaciones de `email` y `password` (obligatorio y formato).
- [x] 1.2 Implementar endpoint `POST /api/auth/login` en el controller con mapeo de respuesta HTTP `200/400/401`.
- [x] 1.3 Incorporar manejo global de errores para devolver mensaje genérico en credenciales inválidas.

## 2. Lógica de autenticación local

- [x] 2.1 Implementar caso de uso en `AuthenticationService` para buscar usuario por email y validar estado activo.
- [x] 2.2 Validar contraseña con BCrypt comparando contra hash persistido.
- [x] 2.3 Garantizar que respuestas y logs no expongan password/hash ni secretos.

## 3. Integración Feign y resiliencia

- [x] 3.1 Implementar cliente OpenFeign para `GET /api/permissions/users/{userId}`.
- [x] 3.2 Configurar `connectTimeout` y `readTimeout` a `2000 ms`.
- [x] 3.3 Implementar fallback de permisos vacíos (`permissions=[]`) en timeout/indisponibilidad remota sin bloquear login.

## 4. Emisión de JWT y configuración

- [x] 4.1 Implementar/ajustar `JwtService` para firmar HS256 con secreto externo (mínimo 256 bits).
- [x] 4.2 Incluir claims obligatorios `sub`, `email`, `permissions`, `iat`, `exp`.
- [x] 4.3 Externalizar propiedades de JWT (secreto, expiración) y documentarlas en configuración del proyecto.

## 5. Pruebas y verificación

- [x] 5.1 Crear pruebas unitarias para login válido con permisos, credenciales inválidas y usuario inactivo.
- [x] 5.2 Crear pruebas unitarias para fallback por error Feign con token emitido y `permissions=[]`.
- [x] 5.3 Crear prueba de integración (`H2` + mock Feign) para flujo HTTP de `POST /api/auth/login`.

## 1. Contrato y validaciones de registro

- [x] 1.1 Crear/ajustar DTOs de `POST /api/auth/register` con validación de nombre, apellido, email, password y passwordConfirm.
- [x] 1.2 Implementar validación de política de contraseña (mínimo 8, mayúscula, minúscula, número y caracter especial).
- [x] 1.3 Configurar respuesta de errores de validación con `400 Bad Request` y detalle de campos.

## 2. Lógica de alta de usuario

- [x] 2.1 Implementar servicio de registro para verificar email único y mapear duplicado a `409 Conflict`.
- [x] 2.2 Persistir usuario activo con rol inicial `REPORT_READER`.
- [x] 2.3 Hashear contraseña exclusivamente con BCrypt antes de persistencia.

## 3. Integración remota y atomicidad

- [x] 3.1 Implementar llamada Feign a `POST /api/permissions/users/{userId}` para asignar permiso inicial.
- [x] 3.2 Definir payload remoto con permiso inicial esperado por contrato (`REPORT:READ`).
- [x] 3.3 Garantizar rollback local si falla asignación remota (timeout, conectividad o error técnico).

## 4. Excepciones, observabilidad y seguridad

- [x] 4.1 Incorporar excepciones de negocio/técnicas (`EmailAlreadyExists`, `PermissionAssignment`, `RegistrationFailed`) y su mapeo HTTP.
- [x] 4.2 Sanitizar logs y respuestas para no exponer password ni hash.
- [x] 4.3 Registrar trazabilidad de alta y rollback con identificador de usuario cuando aplique.

## 5. Pruebas y documentación

- [x] 5.1 Crear pruebas unitarias para registro exitoso, email duplicado, validaciones de contraseña/confirmación.
- [x] 5.2 Crear pruebas unitarias para fallo Feign y verificación de rollback.
- [x] 5.3 Crear prueba de integración con H2 + stub/mocks de autorización para `POST /api/auth/register`.
- [x] 5.4 Documentar configuración de endpoint y comportamiento transaccional en la documentación del servicio.

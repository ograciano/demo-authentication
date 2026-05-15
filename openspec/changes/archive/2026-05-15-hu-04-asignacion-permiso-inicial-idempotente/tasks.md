## 1. Contrato de asignación inicial

- [x] 1.1 Revisar/ajustar `AuthorizationServiceClient` para `POST /api/permissions/users/{userId}`.
- [x] 1.2 Validar que el payload de asignación inicial sea `{ "permission": "REPORT:READ" }`.
- [x] 1.3 Asegurar compatibilidad con DTO de respuesta que incluya `status` idempotente.

## 2. Manejo de idempotencia y errores remotos

- [x] 2.1 Implementar tratamiento exitoso para `status=ASSIGNED`.
- [x] 2.2 Implementar tratamiento exitoso para `status=ALREADY_ASSIGNED`.
- [x] 2.3 Clasificar y mapear errores remotos (`400`, `404`, timeout, `5xx`) según política del servicio.

## 3. Consistencia transaccional en registro

- [x] 3.1 Garantizar rollback local de usuario cuando falle la asignación remota por cualquier causa.
- [x] 3.2 Confirmar que no queden usuarios persistidos tras fallas funcionales (`400/404`) de asignación.
- [x] 3.3 Confirmar que no queden usuarios persistidos tras fallas técnicas (timeout/conectividad/`5xx`) de asignación.

## 4. Observabilidad y seguridad

- [x] 4.1 Registrar trazabilidad por `userId` y resultado remoto (`ASSIGNED`/`ALREADY_ASSIGNED`).
- [x] 4.2 Mantener logs y respuestas sin fuga de datos sensibles.
- [x] 4.3 Instrumentar visibilidad operativa básica de errores de integración y reversiones.

## 5. Pruebas y documentación

- [x] 5.1 Crear/ajustar unit tests para estados idempotentes y clasificación de errores de asignación.
- [x] 5.2 Crear/ajustar pruebas de integración para respuestas remotas `200`, `400`, `404`, timeout.
- [x] 5.3 Agregar cobertura de concurrencia/reintento para evitar duplicados en asignación.
- [x] 5.4 Documentar comportamiento idempotente y política de rollback en artefactos técnicos.

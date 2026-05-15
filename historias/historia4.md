## Historia de Usuario Enriquecida

### Contexto y Objetivo de Negocio
Como `authentication-service`, necesito asignar automáticamente un permiso inicial al registrar usuarios para habilitar su uso básico desde el primer acceso, manteniendo idempotencia y consistencia transaccional.

### Descripción Funcional Detallada
Tras crear un usuario en `POST /api/auth/register`, `authentication-service` consume por Feign el contrato `POST /api/permissions/users/{userId}` de `authorization-service` para asignar permiso inicial (`REPORT:READ`). El contrato valida `userId` y `permission` contra catálogo permitido, normaliza el valor y responde `ASSIGNED` o `ALREADY_ASSIGNED`. Si la asignación falla por validación o disponibilidad, el registro en autenticación debe revertirse.

### Criterios de Aceptación
- [ ] Se consume vía Feign el endpoint `POST /api/permissions/users/{userId}`.
- [ ] Request body obligatorio: `{ "permission": "REPORT:READ" }`.
- [ ] Si la asignación es nueva, responde `200` con `status=ASSIGNED`.
- [ ] Si ya existía, responde `200` con `status=ALREADY_ASSIGNED` (idempotencia).
- [ ] Si `permission` es inválido o vacío, contrato responde `400 Bad Request`.
- [ ] Si `userId <= 0`, contrato responde `400 Bad Request`.
- [ ] Si usuario no existe, contrato responde `404 Not Found`.
- [ ] En fallos durante asignación, `authentication-service` hace rollback del registro.

### Casos Límite y Validaciones
- Permiso con espacios o minúsculas: se normaliza (`trim + uppercase`) en servicio de autorización.
- Permiso fuera de catálogo: rechazo con `400` y mensaje funcional.
- Reintento accidental del registro: no debe duplicar asignación de permiso.
- Falla temporal de red: rollback en auth y error consistente al cliente.

### Requisitos No Funcionales Relevantes
- Integridad: atomicidad registro + permiso.
- Idempotencia: misma asignación no duplica datos.
- Observabilidad: métricas de `ASSIGNED` vs `ALREADY_ASSIGNED` y errores de integración.
- Seguridad: consumo interno service-to-service.
- Persistencia para entorno local/pruebas: H2 en `authentication-service`.

### Consideraciones Técnicas (alineadas al contrato)
- Endpoint remoto: `POST /api/permissions/users/{userId}`.
- Catálogo permitido actual:
  - `REPORT:READ`
  - `REPORT:DOWNLOAD`
  - `ADMIN:MANAGE_PERMISSIONS`
- DTOs sugeridos:
  - `AssignPermissionRequest { permission }`
  - `AssignPermissionResponse { userId, permission, status, timestamp }`
- Integración: OpenFeign con timeout corto y sin reintentos automáticos.

### Requisitos de Testing (alto nivel)
- Unitarias: request de asignación, manejo de estados `ASSIGNED`/`ALREADY_ASSIGNED`, errores de validación.
- Integración: flujo con Feign simulado para `200`, `400`, `404`, timeout.
- Concurrencia: múltiples asignaciones del mismo permiso no generan duplicados.

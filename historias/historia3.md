## Historia de Usuario Enriquecida

### Contexto y Objetivo de Negocio
Como `authentication-service`, necesito consultar permisos vigentes en `authorization-service` para emitir JWT con autorización actualizada y desacoplada del código de autenticación.

### Descripción Funcional Detallada
Durante login, `authentication-service` consume internamente por Feign el contrato `GET /api/permissions/users/{userId}` de `authorization-service` (base URL `http://localhost:8081`). El servicio retorna permisos de negocio para el usuario, normalizados en mayúsculas, sin duplicados y ordenados ascendentemente. Si el usuario no existe, el contrato devuelve `404 Not Found`. En `authentication-service`, fallos de integración deben degradar a `permissions=[]` para no bloquear autenticación.

### Criterios de Aceptación
- [ ] Se consume vía Feign el endpoint `GET /api/permissions/users/{userId}`.
- [ ] `userId` debe ser `Long > 0`.
- [ ] Respuesta `200` contiene `userId`, `permissions[]`, `timestamp`.
- [ ] Si el usuario existe sin permisos, retorna `200` con `permissions=[]`.
- [ ] Si el usuario no existe, el contrato retorna `404` con mensaje `Usuario no encontrado`.
- [ ] En errores de conectividad/timeout en Feign, `authentication-service` aplica fallback a lista vacía.
- [ ] Timeouts configurados en Feign (connect/read = 2000 ms).

### Casos Límite y Validaciones
- `userId <= 0`: `400 Bad Request`.
- Permisos con casing mixto desde origen: salida normalizada a mayúsculas.
- Permisos duplicados: salida sin duplicados.
- Error técnico remoto (`5xx` o timeout): fallback local en autenticación.

### Requisitos No Funcionales Relevantes
- Rendimiento: consulta rápida para no degradar login.
- Resiliencia: fallback en auth para mantener disponibilidad.
- Observabilidad: logging de fallas de integración y tasa de fallback.
- Seguridad: endpoint de uso interno entre servicios.
- Persistencia del dominio de autenticación: H2 para pruebas locales.

### Consideraciones Técnicas (alineadas al contrato)
- Endpoint remoto: `GET /api/permissions/users/{userId}`.
- Contrato de error esperado:
  - `400`: bad request por `userId` inválido.
  - `404`: usuario no encontrado.
- DTO sugerido: `PermissionsResponse { userId, permissions, timestamp }`.
- Integración: `AuthorizationServiceClient` (OpenFeign), sin retry para mantener fail-fast controlado.

### Requisitos de Testing (alto nivel)
- Unitarias: mapeo de respuesta, normalización y fallback.
- Integración: cliente Feign con simulación de `200`, `404`, `400`, timeout.
- E2E: login exitoso con permisos y login exitoso degradado con `permissions=[]`.

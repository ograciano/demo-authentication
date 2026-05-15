## 1. Contrato de integración Feign

- [x] 1.1 Revisar/ajustar `AuthorizationServiceClient` para `GET /api/permissions/users/{userId}` con DTO de respuesta consistente.
- [x] 1.2 Validar precondición de entrada `userId > 0` antes de ejecutar consulta remota.
- [x] 1.3 Confirmar configuración `connectTimeout/readTimeout=2000ms` en propiedades de cliente Feign.

## 2. Normalización y resiliencia en servicio de permisos

- [x] 2.1 Implementar normalización de permisos (trim, uppercase, deduplicado, orden ascendente).
- [x] 2.2 Manejar respuesta remota sin permisos retornando `permissions=[]`.
- [x] 2.3 Implementar fallback a lista vacía en timeout/conectividad/errores técnicos remotos.

## 3. Integración con flujo de autenticación

- [x] 3.1 Verificar que login use la salida normalizada de `PermissionService` para claims JWT.
- [x] 3.2 Asegurar que fallback de permisos no bloquee autenticación exitosa.
- [x] 3.3 Mantener respuesta de login sin fuga de datos sensibles en escenarios de error remoto.

## 4. Pruebas

- [x] 4.1 Crear/ajustar unit tests para normalización y deduplicación de permisos.
- [x] 4.2 Crear/ajustar unit tests para fallback en timeout/conectividad/`5xx`.
- [x] 4.3 Crear/ajustar pruebas de integración para escenarios Feign `200`, `400`, `404` y timeout.
- [x] 4.4 Verificar e2e de login con permisos y login degradado con `permissions=[]`.

## 5. Observabilidad y documentación

- [x] 5.1 Registrar logs de fallas de integración con contexto suficiente (sin secretos).
- [x] 5.2 Documentar comportamiento de fallback y timeouts en artefactos técnicos del servicio.

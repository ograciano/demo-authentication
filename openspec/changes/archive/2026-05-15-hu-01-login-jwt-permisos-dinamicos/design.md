## Context

`authentication-service` debe autenticar con credenciales locales y emitir JWT con permisos dinámicos obtenidos desde `authorization-service`. La capacidad requiere integración remota con tolerancia a fallas: el login no se bloquea ante timeout/indisponibilidad remota y debe continuar con permisos vacíos. El servicio maneja Java 17 + Spring Boot y ya define como reglas obligatorias validación de entrada, BCrypt, JWT HS256 y no exposición de datos sensibles.

## Goals / Non-Goals

**Goals:**
- Exponer `POST /api/auth/login` con contrato HTTP consistente para éxito y errores de negocio.
- Asegurar autenticación local robusta (usuario existente, activo y password BCrypt válida).
- Obtener permisos dinámicos con OpenFeign y timeout corto (`connect/read = 2000 ms`).
- Generar JWT con claims `sub`, `email`, `permissions`, `iat`, `exp`.
- Implementar fallback controlado a `permissions=[]` cuando falle integración remota.

**Non-Goals:**
- No cubre registro de usuario ni asignación inicial de permisos (HU-02 y HU-04).
- No implementa refresh token, revocación ni logout.
- No redefine modelo de autorización ni catálogo de permisos.

## Decisions

1. **Orquestación en servicio de aplicación (`AuthenticationService`)**
   - Decisión: centralizar validación de credenciales, consulta de permisos y emisión JWT en un solo caso de uso.
   - Racional: mantiene el controller delgado y facilita pruebas unitarias por ramas de negocio.
   - Alternativa considerada: lógica fragmentada entre controller y servicios utilitarios; se descarta por menor cohesión y mayor acoplamiento HTTP-negocio.

2. **Cliente OpenFeign para consulta de permisos**
   - Decisión: usar interfaz Feign `AuthorizationServiceClient` para `GET /api/permissions/users/{userId}`.
   - Racional: alinea el contrato interno de integración y simplifica manejo de timeout/errores técnicos.
   - Alternativa considerada: `RestTemplate/WebClient`; se descarta por inconsistencia con requisito explícito de HU-03 (Feign obligatorio).

3. **Fallback explícito en errores de conectividad**
   - Decisión: capturar excepciones de timeout/conectividad Feign y devolver lista vacía para claims.
   - Racional: preserva disponibilidad de autenticación y cumple política “auth no bloqueado por caídas de autorización”.
   - Alternativa considerada: fallar login con 503; se descarta por incumplir regla de negocio.

4. **JWT HS256 con configuración externa**
   - Decisión: firmar token con secreto configurable (mínimo 256 bits) y expiración en propiedades.
   - Racional: cumple seguridad y facilita rotación/configuración por ambiente.
   - Alternativa considerada: hardcode en código; se descarta por riesgo de seguridad y mala operación.

5. **Errores genéricos para credenciales inválidas**
   - Decisión: mapear usuario no encontrado, inactivo o password inválida a `401` con mensaje único.
   - Racional: evita filtración de estado de cuentas y vectores de enumeración de usuarios.
   - Alternativa considerada: mensajes específicos por causa; se descarta por exposición de información sensible.

## Risks / Trade-offs

- **[Riesgo] Dependencia remota degradada puede elevar tokens con permisos vacíos** → **Mitigación**: observabilidad de tasa de fallback y alertamiento para incidentes de autorización-service.
- **[Riesgo] Configuración JWT débil en entornos no productivos** → **Mitigación**: validación de longitud mínima del secreto al iniciar aplicación.
- **[Trade-off] Mayor disponibilidad de login vs. autorización fina inmediata** → **Mitigación**: mantener expiración de token acotada y reforzar controles de autorización en servicios consumidores.

## Migration Plan

1. Agregar configuración de JWT (secreto/expiración) y Feign timeout en propiedades.
2. Implementar endpoint y caso de uso de login con integración Feign.
3. Incorporar handler global de excepciones y sanitización de logs.
4. Ejecutar suite de pruebas unitarias/integración para escenarios HU-01.
5. Desplegar en entorno de pruebas y monitorear métricas de fallback.

Rollback:
- Revertir despliegue a versión previa de `authentication-service`.
- Mantener compatibilidad del endpoint al ser una capacidad aditiva.

## Open Questions

- ¿Existe formato estándar corporativo para claim `permissions` (array de strings vs. scope string)?
- ¿Se requiere prefijo fijo para `tokenType` en la respuesta (por ejemplo `Bearer`) en todos los consumidores?
- ¿Se debe registrar evento de auditoría explícito por login exitoso/fallido además del log técnico?

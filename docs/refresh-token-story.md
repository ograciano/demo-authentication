# Autenticación - Implementar refresh token y renovación JWT

## Historia de Usuario
Como usuario autenticado
quiero renovar mi sesión sin volver a enviar credenciales
para mantener el acceso mientras mi sesión sigue siendo válida.

## Contexto y Objetivo de Negocio
El microservicio de autenticación debe soportar un flujo de renovación basado en refresh token para mejorar la experiencia de sesión y reducir la frecuencia de reautenticación.

## Descripción Funcional Detallada
- El sistema debe emitir un access token y un refresh token al hacer login.
- El endpoint POST /api/auth/refresh debe aceptar un refresh token y devolver nuevos tokens válidos.
- Debe validar firma, expiración, tipo de token, integridad del payload, existencia del usuario y estado activo.
- Debe rechazar access tokens usados como refresh token y tokens alterados o expirados con 401.

## Alcance Incluido
- Generación de access token y refresh token.
- Endpoint /api/auth/refresh.
- Manejo de errores homogéneo para casos inválidos.
- Documentación OpenAPI y pruebas de integración.

## Alcance Excluido
- Renovación de sesión con dispositivos múltiples.
- Revocación persistente de refresh tokens en esta historia.

## Criterios de Aceptación
- [ ] Login devuelve accessToken y refreshToken con expiraciones distintas.
- [ ] Refresh válido devuelve 200 y emite nuevos tokens.
- [ ] Refresh inválido, expirado o de tipo incorrecto devuelve 401.
- [ ] El usuario inactivo no puede renovar sesión.
- [ ] La documentación OpenAPI refleja el nuevo contrato.

## Casos Límite y Validaciones
- Token expirado -> 401.
- Token alterado -> 401.
- Access token usado como refresh token -> 401.
- Usuario inactivo -> 401.

## Reglas de Negocio
- El access token debe contener sub, permissions, iat y exp.
- El refresh token debe contener sub, tokenType=REFRESH, iat y exp.
- El refresh token debe tener una expiración mayor que el access token.
- No debe exponerse ni registrarse el refresh token completo.

## Requisitos No Funcionales
- Seguridad: JWT firmado y validación estricta.
- Rendimiento: renovación en tiempo mínimo.
- Observabilidad: errores homogéneos y sin stacktrace.

## Consideraciones Técnicas
- Backend: AuthService y JwtService.
- Integraciones: compatibilidad con /api/auth/me y login existente.
- Datos: sin cambios estructurales en la entidad de usuario en esta iteración.

## Requisitos de Testing
- Unitarias: generación de refresh token, expiración, claim tokenType y validación criptográfica.
- Integración: login devuelve refresh token, refresh válido, refresh inválido, refresh expirado y acceso con token incorrecto.

## Definition of Ready
- [x] Historia entendida por negocio y desarrollo
- [x] Criterios de aceptación definidos
- [x] Dependencias identificadas
- [x] Open Questions revisadas

## Definition of Done
- [ ] Código implementado
- [ ] Pruebas ejecutadas
- [ ] Criterios de aceptación cubiertos
- [ ] Documentación actualizada
- [ ] Sin errores críticos de calidad o seguridad

## Open Questions
- N/A

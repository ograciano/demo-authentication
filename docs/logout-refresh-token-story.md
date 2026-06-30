# Autenticación - Implementar logout y revocación de refresh token

## Historia de Usuario
Como usuario autenticado
quiero cerrar mi sesión de forma segura
para que mi refresh token ya no pueda reutilizarse.

## Contexto y Objetivo de Negocio
El sistema debe permitir el cierre de sesión y dejar preparado un mecanismo de revocación para impedir futuras renovaciones de JWT con un refresh token previamente usado para logout.

## Descripción Funcional Detallada
- El endpoint POST /api/auth/logout debe aceptar un refresh token y cerrar la sesión asociada.
- Después del logout, el refresh token no debe reutilizarse.
- El flujo de refresh debe rechazar tokens revocados.
- Debe validarse firma, expiración, existencia del usuario, estado del usuario y que el token no haya sido revocado previamente.

## Alcance Incluido
- Endpoint /api/auth/logout.
- Mecanismo mínimo de revocación de refresh tokens.
- Integración del rechazo de tokens revocados en el flujo de refresh.
- Pruebas de integración y OpenAPI.

## Alcance Excluido
- Persistencia distribuida de revocaciones.
- Gestión de sesiones multi-dispositivo avanzada.

## Criterios de Aceptación
- [ ] Logout exitoso devuelve 200 y marca el refresh token como inválido para uso futuro.
- [ ] Un refresh posterior al logout devuelve 401.
- [ ] Un refresh token revocado no puede reutilizarse.
- [ ] Payload inválido devuelve 400 y tokens alterados o inválidos devuelven 401.
- [ ] La documentación OpenAPI refleja el endpoint y respuestas esperadas.

## Casos Límite y Validaciones
- Refresh token inválido -> 401.
- Refresh token revocado -> 401.
- Payload inválido -> 400.

## Reglas de Negocio
- No se debe exponer ni registrar el refresh token completo.
- El error debe ser homogéneo y no exponer detalles internos.
- La sesión debe considerarse terminada tras logout.

## Requisitos No Funcionales
- Seguridad: revocación de sesión y validación estricta.
- Observabilidad: errores homogéneos y sin exposición de secretos.

## Consideraciones Técnicas
- Backend: AuthController, AuthService y flujo de refresh.
- Infraestructura: estrategia mínima de revocación extensible (blacklist en memoria o repositorio temporal).
- Pruebas: logout exitoso, logout inválido y refresh tras logout.

## Requisitos de Testing
- Unitarias: validación de revocación y rechazo de token inválido.
- Integración: logout exitoso, logout con token inválido, refresh posterior a logout y reutilización del token revocado.

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

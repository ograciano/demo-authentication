## Context

HU-04 profundiza el flujo de registro en `authentication-service` enfocándose en la asignación remota del permiso inicial y su idempotencia. La creación de usuario local ya depende de una integración Feign con `authorization-service`; este cambio explicita el tratamiento de estados `ASSIGNED`/`ALREADY_ASSIGNED`, validaciones del contrato remoto y rollback en fallas.

## Goals / Non-Goals

**Goals:**
- Estandarizar invocación Feign a `POST /api/permissions/users/{userId}` con payload obligatorio `REPORT:READ`.
- Aceptar ambos estados exitosos remotos (`ASSIGNED`, `ALREADY_ASSIGNED`) como resultado válido del flujo.
- Conservar consistencia transaccional del registro: rollback local en errores de validación remota o disponibilidad.
- Mejorar trazabilidad operativa de resultados idempotentes y fallos de integración.

**Non-Goals:**
- No redefine catálogo remoto de permisos.
- No cambia contrato de login ni generación de JWT.
- No implementa mecanismos de retry automáticos o colas de compensación asíncronas.

## Decisions

1. **Estado remoto idempotente tratado como éxito funcional**
   - Decisión: aceptar `ASSIGNED` y `ALREADY_ASSIGNED` como semánticamente exitosos.
   - Racional: evita efectos secundarios en reintentos y cumple idempotencia.
   - Alternativa: tratar `ALREADY_ASSIGNED` como error; se descarta por romper el contrato remoto.

2. **Payload fijo y explícito para permiso inicial**
   - Decisión: enviar `permission=REPORT:READ` desde configuración, con serialización explícita en DTO.
   - Racional: mantiene consistencia de onboarding y permite parametrización por ambiente.
   - Alternativa: hardcode sin propiedad; se descarta por menor mantenibilidad.

3. **Rollback de registro en cualquier fallo de asignación**
   - Decisión: mantener estrategia de rollback local ante `400/404/5xx/timeout` durante asignación.
   - Racional: evita usuarios activos sin permiso inicial.
   - Alternativa: persistir usuario y reconciliar luego; se descarta por incumplir integridad requerida.

4. **Observabilidad por resultado de asignación**
   - Decisión: registrar resultado de asignación (`ASSIGNED`/`ALREADY_ASSIGNED`) y fallas con `userId`.
   - Racional: facilita monitoreo de idempotencia y diagnóstico de incidentes de integración.
   - Alternativa: logs genéricos sin estado remoto; se descarta por menor valor operativo.

## Risks / Trade-offs

- **[Riesgo] Inconsistencia si contrato remoto cambia estados esperados** → **Mitigación**: validación estricta y pruebas de integración de contrato.
- **[Riesgo] Dependencia remota reduce disponibilidad de registro** → **Mitigación**: timeouts cortos + errores controlados y rollback explícito.
- **[Trade-off] Integridad estricta vs. throughput de registro en incidentes remotos** → **Mitigación**: priorizar consistencia y medir tasa de fallos para decisiones futuras.

## Migration Plan

1. Ajustar manejo de respuesta de asignación para estados idempotentes.
2. Reforzar clasificación de errores remotos en flujo de registro y rollback.
3. Ampliar pruebas unitarias/integración para `ASSIGNED`, `ALREADY_ASSIGNED`, `400`, `404`, timeout.
4. Documentar en contratos técnicos política idempotente y criterios de rollback.

Rollback:
- Revertir cambios en servicio de registro y DTOs de integración.
- Mantener versión previa del flujo si surgen incompatibilidades con `authorization-service`.

## Open Questions

- ¿Se requiere exponer `status` de asignación (`ASSIGNED`/`ALREADY_ASSIGNED`) en respuesta pública de registro?
- ¿El catálogo de permisos iniciales variará por tipo de usuario en siguientes iteraciones?
- ¿Se debe instrumentar métrica separada para reintentos de registro que terminan en `ALREADY_ASSIGNED`?

## Context

HU-03 formaliza la integración interna entre `authentication-service` y `authorization-service` para consulta dinámica de permisos durante login. Aunque ya existe consumo básico, se requiere especificar explícitamente validación de `userId`, normalización de permisos y conducta resiliente ante fallas remotas para asegurar disponibilidad de autenticación.

## Goals / Non-Goals

**Goals:**
- Definir un contrato de integración Feign claro para `GET /api/permissions/users/{userId}`.
- Validar `userId > 0` antes de consulta remota.
- Normalizar permisos (uppercase, únicos, ordenados) para claims JWT consistentes.
- Configurar y mantener timeouts de integración en `2000 ms` (connect/read).
- Aplicar fallback a `permissions=[]` en timeout/conectividad/errores técnicos.

**Non-Goals:**
- No modifica reglas de autenticación de credenciales del login.
- No cubre alta de usuarios ni asignación inicial de permisos (HU-02/HU-04).
- No cambia el modelo de permisos en `authorization-service`.

## Decisions

1. **Validación temprana de `userId` en servicio de permisos**
   - Decisión: rechazar valores inválidos (`<=0`) antes de invocar Feign.
   - Racional: evita tráfico remoto innecesario y hace explícito el contrato local.
   - Alternativa: delegar validación al servicio remoto; se descarta por menor control local.

2. **Normalización canónica en `PermissionService`**
   - Decisión: aplicar `trim + uppercase + dedupe + sort` a la lista recibida.
   - Racional: desacopla al consumidor del formato remoto y estabiliza claims del JWT.
   - Alternativa: usar respuesta cruda; se descarta por inconsistencias de casing y orden.

3. **Fallback controlado en fallas técnicas de integración**
   - Decisión: en errores Feign (timeout/conectividad/`5xx`) devolver lista vacía.
   - Racional: preserva disponibilidad de login y cumple regla de negocio de degradación.
   - Alternativa: propagar error y bloquear login; se descarta por incumplimiento funcional.

4. **Timeouts cortos y fail-fast**
   - Decisión: mantener `connectTimeout/readTimeout=2000ms`, sin retry automático.
   - Racional: limita latencia total del login y evita bloqueos prolongados.
   - Alternativa: retries automáticos; se descarta por incremento de latencia acumulada.

## Risks / Trade-offs

- **[Riesgo] Fallback frecuente reduce calidad de autorización en tokens** → **Mitigación**: métrica y alertas de tasa de fallback para actuar sobre disponibilidad remota.
- **[Riesgo] Normalización local podría ocultar defectos aguas arriba** → **Mitigación**: logging acotado de anomalías y pruebas de integración del contrato remoto.
- **[Trade-off] Alta disponibilidad de login vs. precisión inmediata de permisos** → **Mitigación**: TTL de token acotado y verificación en servicios protegidos.

## Migration Plan

1. Ajustar `PermissionService` para validación local de `userId` y normalización robusta.
2. Confirmar configuración Feign de timeouts en propiedades.
3. Revisar mapeo de errores remotos para fallback únicamente en fallas técnicas.
4. Añadir pruebas unitarias e integración para `200/400/404/timeout`.
5. Validar flujo login con y sin fallback.

Rollback:
- Revertir cambios de servicio/configuración a la versión previa.
- Mantener compatibilidad del login al ser ajuste interno de integración.

## Open Questions

- ¿Se requiere distinguir explícitamente entre `404` remoto y falla técnica para observabilidad de negocio?
- ¿Debe agregarse métrica dedicada por tipo de error Feign (`timeout`, `5xx`, red)?
- ¿Conviene exponer porcentaje de fallback en dashboard de autenticación como KPI operativo?

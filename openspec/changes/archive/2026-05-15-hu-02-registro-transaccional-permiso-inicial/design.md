## Context

HU-02 requiere registrar usuarios en `authentication-service` con consistencia entre persistencia local e integración remota de permisos. El flujo incluye validaciones de entrada, protección de credenciales (BCrypt) y una operación compuesta donde la creación local y la asignación remota deben comportarse de forma atómica desde el punto de vista funcional.

## Goals / Non-Goals

**Goals:**
- Exponer `POST /api/auth/register` con validaciones robustas y respuestas HTTP consistentes (`201`, `400`, `409`, `500/503`).
- Garantizar que el usuario se cree activo con rol `REPORT_READER` y contraseña hasheada con BCrypt.
- Asignar permiso inicial mediante Feign en el mismo flujo de registro.
- Asegurar rollback local cuando falle la asignación remota para evitar usuarios persistidos sin permiso inicial.

**Non-Goals:**
- No cubre login JWT ni fallback de permisos en autenticación (HU-01).
- No cubre administración completa de roles/permisos ni catálogo remoto (HU-03/HU-04).
- No introduce refresh tokens ni cambios de seguridad fuera del flujo de registro.

## Decisions

1. **Orquestación transaccional en `UserRegistrationService`**
   - Decisión: encapsular en un único servicio de aplicación la validación, alta local, llamada Feign y compensación.
   - Racional: concentra reglas de negocio y simplifica testeo de escenarios de rollback.
   - Alternativa: repartir lógica entre controller y adapters; se descarta por acoplamiento y menor trazabilidad.

2. **Persistencia local primero, asignación remota después**
   - Decisión: guardar usuario y luego invocar asignación de permiso inicial.
   - Racional: permite obtener `userId` requerido por endpoint remoto.
   - Alternativa: reservar identificador previo o flujo inverso; se descarta por complejidad sin beneficio inmediato.

3. **Rollback explícito ante error remoto**
   - Decisión: en error de integración, fallar registro y revertir usuario local.
   - Racional: cumple requisito de atomicidad funcional de HU-02.
   - Alternativa: marcar estado pendiente de permiso y resolver asíncrono; se descarta porque dejaría inconsistencia temporal.

4. **Validación fuerte de contraseña y confirmación**
   - Decisión: validar `password`/`passwordConfirm` y política mínima (longitud y complejidad) antes de persistir.
   - Racional: evita escrituras inválidas y refuerza seguridad desde el borde.
   - Alternativa: validar solo en frontend; se descarta por no ser confiable.

5. **Errores de negocio con mapeo explícito**
   - Decisión: mapear duplicado de email a `409`, validaciones a `400`, fallos técnicos de integración a `500/503`.
   - Racional: contrato estable para consumidores y observabilidad clara por categoría.
   - Alternativa: devolver `500` genérico para todo; se descarta por baja calidad de contrato.

## Risks / Trade-offs

- **[Riesgo] Caídas de `authorization-service` bloquean registros válidos** → **Mitigación**: timeouts cortos y clasificación de error técnica para diagnóstico rápido.
- **[Riesgo] Rollback parcial por errores no controlados** → **Mitigación**: transacción local delimitada y pruebas de integración de rollback.
- **[Trade-off] Menor disponibilidad de registro frente a consistencia estricta** → **Mitigación**: priorizar integridad y evaluar colas/eventos en una futura evolución.

## Migration Plan

1. Añadir DTOs y validadores para `POST /api/auth/register`.
2. Implementar servicio de registro con BCrypt, rol inicial y rollback.
3. Integrar llamada Feign de asignación de permiso inicial.
4. Incorporar excepciones y mapeo HTTP consistente.
5. Agregar pruebas unitarias e integración (`H2 + stub/mocks`) para escenarios críticos.

Rollback:
- Revertir despliegue a versión anterior del servicio.
- Mantener endpoint de registro deshabilitado o previo según estrategia de release.

## Open Questions

- ¿La respuesta de registro debe incluir `role` explícito además de `userId`, `email` y nombre?
- ¿La política de error para fallos remotos debe ser siempre `503` o diferenciar `timeout` vs `error funcional remoto`?
- ¿Se requiere auditoría formal de eventos de alta/rollback además de logging técnico?

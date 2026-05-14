# Template: AGENTS.md

Copia este template y personalízalo para tu proyecto.

---

## Instrucciones de Uso

1. **Copia** el contenido del bloque de código de abajo
2. **Crea** un archivo llamado `AGENTS.md` en la raíz de tu proyecto
3. **Rellena** cada sección con la información de tu proyecto
4. **Elimina** las secciones que no apliquen
5. **Agrega** secciones adicionales si las necesitas

---

## Template

```markdown
# Instrucciones para el Agente IA

## Contexto del Proyecto

<!-- Describe brevemente qué es este proyecto -->

- **Nombre**: [Nombre del proyecto]
- **Tipo**: [API / Web App / Mobile / Library / CLI / etc.]
- **Propósito**: [Descripción en 1-2 oraciones]
- **Arquitectura**: [Clean Architecture / MVC / Microservices / etc.]

## Stack Tecnológico

### Core
<!-- Lista las tecnologías principales con versiones -->
- **Lenguaje**: [Ej: Python 3.11+ / TypeScript 5.0+ / Java 17+]
- **Framework**: [Ej: FastAPI / React / Spring Boot]
- **Base de datos**: [Ej: PostgreSQL 15+ / MongoDB 6+]

### Testing
<!-- Lista las herramientas de testing -->
- **Framework**: [Ej: pytest / Vitest / JUnit]
- **Coverage**: [Ej: pytest-cov / c8 / JaCoCo]

### Herramientas
<!-- Lista las herramientas de desarrollo -->
- **Linting**: [Ej: Ruff / ESLint / Checkstyle]
- **Formatting**: [Ej: Black / Prettier / google-java-format]
- **Dependencias**: [Ej: Poetry / pnpm / Maven]

## Estructura del Proyecto

<!-- Describe la estructura de carpetas principal -->

```
nombre-proyecto/
├── src/                    # Código fuente
│   ├── [capa-1]/           # [Descripción]
│   ├── [capa-2]/           # [Descripción]
│   └── [capa-3]/           # [Descripción]
├── tests/                  # Tests
│   ├── unit/               # Tests unitarios
│   └── integration/        # Tests de integración
├── [otras-carpetas]/       # [Descripción]
├── AGENTS.md
└── [archivos-config]
```

## Convenciones de Código

### Nomenclatura

<!-- Define las convenciones de nombres -->

| Elemento | Convención | Ejemplo |
|----------|------------|---------|
| Variables | [snake_case / camelCase] | `user_name` / `userName` |
| Funciones | [snake_case / camelCase] | `get_user()` / `getUser()` |
| Clases | PascalCase | `UserService` |
| Constantes | UPPER_SNAKE_CASE | `MAX_RETRIES` |
| Archivos | [snake_case / kebab-case] | `user_service.py` / `user-service.ts` |

### Formato de Código

<!-- Define reglas de formato -->
- **Líneas**: Máximo [80 / 100 / 120] caracteres
- **Indentación**: [2 / 4] espacios
- **Strings**: Comillas [simples / dobles]
- **Imports**: Ordenados [alfabéticamente / por tipo]

### Documentación

<!-- Define el estilo de documentación -->
- **Docstrings**: Formato [Google / NumPy / JSDoc]
- **Comentarios**: En [español / inglés]
- **README**: [Requerimientos mínimos]

#### Ejemplo de Documentación

```[lenguaje]
# Agrega un ejemplo de cómo documentar una función
```

## Patrones a Seguir

### [Patrón 1: Ej. Manejo de Errores]

<!-- Describe el patrón con ejemplo -->

```[lenguaje]
# Ejemplo de código correcto
```

### [Patrón 2: Ej. Validación de Inputs]

<!-- Describe el patrón con ejemplo -->

```[lenguaje]
# Ejemplo de código correcto
```

### [Patrón 3: Ej. Logging]

<!-- Describe el patrón con ejemplo -->

```[lenguaje]
# Ejemplo de código correcto
```

## Restricciones de Seguridad

### PROHIBIDO (NUNCA hacer)

<!-- Lista las prácticas prohibidas con ejemplos -->

1. **[Prohibición 1]**
   ```[lenguaje]
   # ❌ INCORRECTO
   [código malo]
   
   # ✅ CORRECTO
   [código bueno]
   ```

2. **[Prohibición 2]**
   ```[lenguaje]
   # ❌ INCORRECTO
   [código malo]
   
   # ✅ CORRECTO
   [código bueno]
   ```

3. **[Prohibición 3]**
   <!-- Agregar más según necesites -->

### OBLIGATORIO (SIEMPRE hacer)

<!-- Lista las prácticas obligatorias -->

1. **[Obligación 1]**: [Descripción breve]
2. **[Obligación 2]**: [Descripción breve]
3. **[Obligación 3]**: [Descripción breve]
4. **[Obligación 4]**: [Descripción breve]
5. **[Obligación 5]**: [Descripción breve]

## Estándares de Testing

### Estructura de Tests

<!-- Define la estructura de carpetas de tests -->

```
tests/
├── unit/
│   └── [estructura]
├── integration/
│   └── [estructura]
└── [otros-tipos]/
```

### Nomenclatura de Tests

<!-- Define el patrón de nombres de tests -->

```[lenguaje]
# Patrón: [tu patrón]
# Ejemplo:
def test_[función]_[escenario]_[resultado]():
    pass
```

### Estructura de Test (AAA)

```[lenguaje]
def test_ejemplo():
    # Arrange (Preparar)
    [setup]
    
    # Act (Actuar)
    [ejecución]
    
    # Assert (Verificar)
    [verificaciones]
```

## Reglas de Negocio

<!-- Documenta reglas específicas del dominio si aplica -->

### [Entidad/Concepto 1]

- [Regla 1]
- [Regla 2]
- [Regla 3]

### [Entidad/Concepto 2]

- [Regla 1]
- [Regla 2]

## Workflows del Equipo

<!-- Documenta procesos del equipo si aplica -->

### Commits

```
<tipo>(<alcance>): <descripción>

Tipos: feat, fix, refactor, test, docs, chore
```

### Branches

- `main`: Producción
- `develop`: Integración
- `feature/<descripción>`: Nuevas funcionalidades
- `fix/<descripción>`: Correcciones

### Pull Requests

- [Requisito 1]
- [Requisito 2]
- [Requisito 3]

## Ejemplos de Referencia

### [Tipo de Código 1: Ej. Service]

```[lenguaje]
# Ejemplo completo de código que representa el estilo deseado
```

### [Tipo de Código 2: Ej. Controller/Handler]

```[lenguaje]
# Ejemplo completo de código que representa el estilo deseado
```

### [Tipo de Código 3: Ej. Test]

```[lenguaje]
# Ejemplo completo de test que representa el estilo deseado
```

---

<!-- Metadata del archivo -->
**Última actualización**: [Fecha]
**Versión**: [1.0]
```

---

## Checklist de Revisión

Antes de finalizar tu AGENTS.md, verifica:

- [ ] **Contexto**: ¿Está claro qué es y para qué sirve el proyecto?
- [ ] **Stack**: ¿Están listadas todas las tecnologías con versiones?
- [ ] **Estructura**: ¿Está documentada la estructura de carpetas?
- [ ] **Nomenclatura**: ¿Están definidas las convenciones de nombres?
- [ ] **Seguridad**: ¿Hay al menos 3-5 restricciones de seguridad?
- [ ] **Ejemplos**: ¿Hay ejemplos de código que muestren el estilo?
- [ ] **Concisión**: ¿El archivo tiene menos de 500 líneas?
- [ ] **Actualización**: ¿Hay fecha de última actualización?

---

## Tips para un AGENTS.md Efectivo

1. **Sé específico**: "Usa snake_case" es mejor que "usa buen estilo"
2. **Incluye ejemplos**: El código de ejemplo vale más que descripciones
3. **Prioriza**: Pon lo más importante al principio
4. **Actualiza**: Revisa y actualiza regularmente
5. **Valida**: Prueba que la IA sigue las instrucciones

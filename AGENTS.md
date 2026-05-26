# AGENTS.md - demo-authentication

## Contexto del Proyecto

- **Nombre**: `demo-authentication`
- **Tipo**: API backend (microservicio de autenticación)
- **Dominio**: autenticación de usuarios con JWT e integración interna con servicio de autorización
- **Arquitectura objetivo**: Clean/Hexagonal ligera sobre Spring Boot

## Objetivo Funcional

Este servicio debe cubrir, como base funcional:

1. Registro de usuario seguro.
2. Login con credenciales y emisión de JWT.
3. Consulta de permisos de usuario vía integración interna (`authorization-service`).
4. Asignación de permiso predeterminado al registrar usuario nuevo.

Historias de referencia locales:
- `historias/HU-01-Autenticacion-Login-JWT.txt`
- `historias/HU-02-Autenticacion-Registro-Usuario.txt`
- `historias/HU-03-Consultar-Permisos-Dinamicamente.txt`
- `historias/HU-04-Asignar-Permiso-Predeterminado.txt`

Contrato de integración H3/H4:
- `contracts/authentication-service-h3-h4-contract.md`

## Stack Tecnológico

### Core

- **Lenguaje**: Java 17
- **Framework**: Spring Boot `3.5.14`
- **Seguridad**: Spring Security
- **Persistencia**: Spring Data JPA
- **DB runtime local**: H2
- **Documentación API**: springdoc-openapi (`2.8.16`)
- **Build tool**: Maven Wrapper (`./mvnw`)

### Testing

- JUnit 5 (vía `spring-boot-starter-test`)
- Spring Security Test
- Spring Boot Testcontainers + Testcontainers JUnit Jupiter

## Estructura del Proyecto

Estructura actual:

```text
demo-authentication/
├── src/main/java/com/vass/authentication/
├── src/main/resources/
├── src/test/java/com/vass/authentication/
├── historias/
├── contracts/
├── mcp/jira-writer/
├── .github/agents/
└── pom.xml
```

Estructura objetivo recomendada para nuevas implementaciones:

```text
src/main/java/com/vass/authentication/
├── api/                  # Controllers, request/response DTOs, exception handlers HTTP
├── application/          # Casos de uso y puertos
├── domain/               # Entidades, reglas de negocio y excepciones de dominio
├── infrastructure/       # JPA adapters, clientes externos (Feign), seguridad/JWT
└── config/               # Beans, seguridad, OpenAPI, configuración técnica
```

## Convenciones de Código

### Nomenclatura

| Elemento | Convención | Ejemplo |
|---|---|---|
| Paquetes | minúsculas | `com.vass.authentication.application.service` |
| Clases | PascalCase | `AuthController` |
| Métodos/variables | camelCase | `generateToken` |
| Constantes | UPPER_SNAKE_CASE | `TOKEN_EXPIRATION_SECONDS` |
| DTOs | sufijo `Request` / `Response` | `LoginRequest`, `LoginResponse` |

### Reglas de Implementación

1. Controllers sin lógica de negocio.
2. Validación de entrada con Jakarta Validation en DTOs.
3. Mapear errores a respuestas HTTP consistentes (`timestamp`, `status`, `error`, `message`, `path`).
4. No exponer secretos, contraseñas ni hashes en respuestas o logs.
5. Usar transacciones en operaciones de escritura que requieran atomicidad.

## Contratos y Reglas de Integración Interna

Para H3/H4, el contrato fuente de verdad es:
- `contracts/authentication-service-h3-h4-contract.md`

### GET `/api/permissions/users/{userId}`

- `userId > 0`
- `200` con `permissions` (vacía si usuario existe sin permisos)
- `400` si `userId <= 0`
- `404` si usuario no existe

### POST `/api/permissions/users/{userId}`

- Body: `{ "permission": "REPORT:READ" }`
- Catálogo permitido (`app.permissions.allowed`):
  - `REPORT:READ`
  - `REPORT:DOWNLOAD`
  - `INTERNAL:PERMISSIONS_READ`
- Respuestas:
  - `200` `ASSIGNED`
  - `200` `ALREADY_ASSIGNED`
  - `400` por payload inválido o permiso no permitido
  - `404` si usuario no existe

## Seguridad (Obligatorio)

### PROHIBIDO

1. Guardar contraseñas en texto plano.
2. Loguear contraseñas, tokens completos, API keys o secretos.
3. Exponer detalles internos sensibles en errores 5xx.
4. Hardcodear permisos/roles fuera del contrato o configuración.

### OBLIGATORIO

1. Hash de contraseñas con BCrypt.
2. JWT firmado con expiración explícita.
3. Diferenciar errores de autenticación/autorización (`401` vs `403`) donde aplique.
4. Validar estrictamente entradas de endpoints y de integraciones internas.
5. Mantener políticas de rollback en flujos atómicos (registro + asignación de permiso).

## Testing

### Alcance mínimo por cambio funcional

1. **Unit tests** para reglas de negocio y validaciones.
2. **Integration tests** para endpoints principales (`/api/auth/login`, `/api/auth/register`).
3. **Contract-oriented tests** para integración H3/H4 (códigos HTTP y payloads esperados).
4. **Security tests** para escenarios de credenciales inválidas/no autorizadas.

### Convención sugerida de nombres

- `test<Componente>_<Escenario>_<ResultadoEsperado>`
- Ejemplo: `testAuthService_InvalidPassword_ReturnsUnauthorized`

## Documentación y OpenAPI

Cada cambio que modifique contratos HTTP debe:

1. Reflejarse en OpenAPI/springdoc.
2. Mantener ejemplos de request/response consistentes con historias/contratos.
3. Actualizar archivos funcionales en `historias/` y/o `contracts/` cuando cambien reglas.

## Reglas para Agentes IA en este Repo

1. Priorizar `AGENTS.md` + `contracts/` + `historias/` como fuente de verdad funcional/técnica.
2. No asumir comportamiento no documentado; si falta dato, registrarlo como pregunta abierta.
3. Si una historia contradice contrato, prevalece el contrato vigente y se propone ajuste de historia.
4. En tareas Jira de este proyecto (`SCRUM`), usar labels:
   - `ai-generated`
   - `ai-enriched`
   - `mcp-managed`
   - `needs-review`
   - `openspec` (cuando aplique)
5. Evitar transiciones de estado Jira automáticas sin aprobación explícita del usuario.

## Checklist de Definición

### Definition of Ready (DoR)

- Historia con objetivo claro y criterios verificables.
- Contratos/API involucrados definidos o referenciados.
- Dependencias y supuestos identificados.
- Reglas de seguridad y errores esperados especificados.

### Definition of Done (DoD)

- Código implementado y compilando.
- Tests relevantes pasando.
- Criterios de aceptación cubiertos.
- Contratos/documentación actualizados.
- Sin exposición de datos sensibles.

JWT Contract (authentication-service)

Claims:
- sub: username
- permissions: List<String>

The token does NOT contain userId.

---

**Versión**: 1.0.0  
**Última actualización**: 2026-05-19  
**Fuente principal**: `template-agents-md.md` + `Architecture_AGENTS_Generator.md` + contexto real del repo.

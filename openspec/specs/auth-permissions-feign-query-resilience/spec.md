# auth-permissions-feign-query-resilience Specification

## Purpose
TBD - created by archiving change hu-03-consulta-dinamica-permisos-feign. Update Purpose after archive.
## Requirements
### Requirement: Permission query uses OpenFeign contract with valid userId
The system SHALL query user permissions through OpenFeign against `GET /api/permissions/users/{userId}` and MUST validate that `userId` is a positive `Long` before remote invocation.

#### Scenario: Consulta remota válida
- **WHEN** authentication-service requests permissions with `userId > 0`
- **THEN** the system invokes the Feign client against `GET /api/permissions/users/{userId}`

#### Scenario: userId inválido
- **WHEN** authentication-service attempts permission lookup with `userId <= 0`
- **THEN** the system rejects the request as invalid without relying on successful remote call

### Requirement: Permission payload is normalized for JWT consumption
The system MUST normalize remote permissions to uppercase, remove duplicates, and return them in ascending order.

#### Scenario: Permisos mixtos y duplicados
- **WHEN** remote response includes mixed-case and duplicated permissions
- **THEN** the resulting permission list is uppercase, unique, and sorted ascending

#### Scenario: Usuario sin permisos
- **WHEN** remote response indicates existing user with no permissions
- **THEN** the resulting permission list is empty (`[]`)

### Requirement: Technical failures degrade to empty permissions
If permission retrieval fails due to timeout, connectivity issues, or remote technical error, authentication-service MUST continue login flow with `permissions=[]`.

#### Scenario: Timeout o indisponibilidad remota
- **WHEN** Feign request to authorization-service times out or cannot reach the service
- **THEN** permission lookup result is `[]` and authentication flow remains available

#### Scenario: Error remoto 5xx
- **WHEN** authorization-service responds with technical server error
- **THEN** permission lookup result is `[]` for resilient login behavior

### Requirement: Feign timeout configuration is bounded
The system SHALL configure Feign client with `connectTimeout=2000ms` and `readTimeout=2000ms` for permission queries.

#### Scenario: Configuración aplicada
- **WHEN** authentication-service starts with default integration settings
- **THEN** Feign permission query client uses connect/read timeout values of 2000 milliseconds


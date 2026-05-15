# auth-user-registration-transactional-initial-permission Specification

## Purpose
TBD - created by archiving change hu-02-registro-transaccional-permiso-inicial. Update Purpose after archive.
## Requirements
### Requirement: Register endpoint validates input and creates active user
The system SHALL expose `POST /api/auth/register` and MUST validate required fields (`name`, `lastName`, `email`, `password`, `passwordConfirm`) before processing. On valid input, the system SHALL create an active user with initial role `REPORT_READER`.

#### Scenario: Registro exitoso con datos válidos
- **WHEN** a client sends valid registration data with a non-registered email
- **THEN** the system returns `201 Created` with non-sensitive user data

#### Scenario: Error de validación de entrada
- **WHEN** a client sends invalid email, empty name/lastName, weak password, or mismatched `passwordConfirm`
- **THEN** the system returns `400 Bad Request` with validation details

### Requirement: Registration enforces unique email
The system MUST reject user registration when the email already exists in local persistence.

#### Scenario: Email duplicado
- **WHEN** a client sends registration data with an existing email
- **THEN** the system returns `409 Conflict`

### Requirement: Password is stored only as BCrypt hash
The system MUST persist password credentials only as BCrypt hash and SHALL NOT expose raw password or hash in response payloads.

#### Scenario: Persistencia segura de contraseña
- **WHEN** registration succeeds
- **THEN** the stored credential is BCrypt hash and response excludes password fields

### Requirement: Initial permission assignment is part of transactional registration
The system SHALL assign initial permission through OpenFeign using `POST /api/permissions/users/{userId}` as part of registration flow.

#### Scenario: Asignación inicial exitosa
- **WHEN** user creation succeeds and authorization-service accepts initial permission assignment
- **THEN** registration completes successfully with `201 Created`

### Requirement: Registration must rollback on remote permission assignment failure
If remote permission assignment fails due to timeout, connectivity, or technical integration error, the system MUST fail registration and MUST NOT keep the user persisted locally.

#### Scenario: Falla remota con rollback local
- **WHEN** user is created locally but permission assignment fails in authorization-service
- **THEN** the system returns controlled technical error (`500` or `503`) and no user remains persisted


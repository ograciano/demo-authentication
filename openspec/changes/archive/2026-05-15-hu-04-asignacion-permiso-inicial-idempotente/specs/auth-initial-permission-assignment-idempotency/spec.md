## ADDED Requirements

### Requirement: Registration assigns initial permission through Feign contract
The system SHALL invoke `POST /api/permissions/users/{userId}` through OpenFeign during user registration and MUST send the required payload `{ "permission": "REPORT:READ" }`.

#### Scenario: Request de asignación inicial válido
- **WHEN** a new user is created successfully in authentication-service
- **THEN** the system invokes remote permission assignment with `permission=REPORT:READ`

### Requirement: Remote idempotent statuses are accepted as success
The system MUST treat remote assignment statuses `ASSIGNED` and `ALREADY_ASSIGNED` as successful outcomes for the registration flow.

#### Scenario: Permiso asignado por primera vez
- **WHEN** authorization-service responds with `status=ASSIGNED`
- **THEN** registration flow continues as successful

#### Scenario: Permiso ya asignado (idempotencia)
- **WHEN** authorization-service responds with `status=ALREADY_ASSIGNED`
- **THEN** registration flow continues as successful without duplicate side effects

### Requirement: Validation and not-found errors from remote are handled consistently
The system SHALL handle remote validation errors (`400`) and user-not-found (`404`) as registration failures requiring controlled error mapping.

#### Scenario: Permiso inválido o vacío en contrato remoto
- **WHEN** remote service returns `400 Bad Request` for invalid permission payload
- **THEN** authentication-service treats registration as failed and returns controlled error response

#### Scenario: userId no encontrado remotamente
- **WHEN** remote service returns `404 Not Found` during assignment
- **THEN** authentication-service treats registration as failed and returns controlled error response

### Requirement: Registration rollback is mandatory on permission assignment failure
If remote permission assignment fails due to validation, not found, timeout, connectivity, or technical error, authentication-service MUST rollback local user creation.

#### Scenario: Timeout o error técnico remoto
- **WHEN** permission assignment call fails by timeout, connectivity or `5xx`
- **THEN** no user remains persisted in authentication-service after request completion

#### Scenario: Falla funcional remota con rollback
- **WHEN** permission assignment returns functional failure (`400`/`404`)
- **THEN** no user remains persisted in authentication-service after request completion

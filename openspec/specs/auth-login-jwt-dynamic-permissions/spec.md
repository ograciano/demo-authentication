# auth-login-jwt-dynamic-permissions Specification

## Purpose
TBD - created by archiving change hu-01-login-jwt-permisos-dinamicos. Update Purpose after archive.
## Requirements
### Requirement: Login endpoint authenticates active users with validated credentials
The system SHALL expose `POST /api/auth/login` and MUST validate required fields and format before authentication. The system SHALL authenticate only existing active users with valid BCrypt password and return a standardized success response.

#### Scenario: Login exitoso con credenciales válidas
- **WHEN** a client sends a valid `email` and `password` for an existing active user
- **THEN** the system returns `200 OK` with `tokenType`, `accessToken`, `expiresIn`, `userId`, and `username`

#### Scenario: Validación de entrada inválida
- **WHEN** a client sends an empty email, invalid email format, or empty password
- **THEN** the system returns `400 Bad Request` with validation errors

#### Scenario: Credenciales inválidas o usuario inactivo
- **WHEN** a client sends non-existent email, wrong password, or an inactive user account
- **THEN** the system returns `401 Unauthorized` with a generic error message

### Requirement: JWT includes identity and dynamic permission claims
The system SHALL issue an HS256-signed JWT containing `sub`, `email`, `permissions`, `iat`, and `exp` claims. The token payload MUST include permissions resolved for the authenticated user at login time.

#### Scenario: Token contiene claims obligatorios
- **WHEN** login succeeds
- **THEN** the issued JWT contains `sub` as `userId`, `email`, `permissions`, `iat`, and `exp`

#### Scenario: Permisos dinámicos incluidos en token
- **WHEN** authorization-service returns permissions for the authenticated user
- **THEN** the JWT claim `permissions` contains the normalized permission list

### Requirement: Permissions are fetched through Feign with resilient fallback
The system SHALL query permissions using OpenFeign against `GET /api/permissions/users/{userId}` with `connectTimeout=2000ms` and `readTimeout=2000ms`. If permission retrieval fails due to timeout or service unavailability, the system MUST continue login and issue JWT with `permissions=[]`.

#### Scenario: Consulta remota de permisos exitosa
- **WHEN** authorization-service responds successfully for `userId`
- **THEN** the system uses returned permissions to build the JWT

#### Scenario: Timeout o indisponibilidad remota
- **WHEN** authorization-service times out or is unavailable during permission lookup
- **THEN** the system still returns `200 OK` and issues JWT with `permissions=[]`

### Requirement: Authentication flow does not expose sensitive data
The system MUST NOT include passwords, password hashes, or equivalent secrets in HTTP responses, logs, or error payloads.

#### Scenario: Respuesta y logs sanitizados
- **WHEN** login succeeds or fails
- **THEN** no password or hash data is present in response bodies or application logs


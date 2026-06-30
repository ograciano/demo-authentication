package com.vass.authentication.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vass.authentication.api.dto.ApiErrorResponse;
import com.vass.authentication.api.dto.LoginRequest;
import com.vass.authentication.api.dto.LoginResponse;
import com.vass.authentication.api.dto.MeResponse;
import com.vass.authentication.api.dto.RefreshRequest;
import com.vass.authentication.api.dto.RefreshResponse;
import com.vass.authentication.api.dto.RegisterRequest;
import com.vass.authentication.api.dto.RegisterResponse;
import com.vass.authentication.application.port.in.GetCurrentUserUseCase;
import com.vass.authentication.application.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public AuthController(AuthService authService, GetCurrentUserUseCase getCurrentUserUseCase) {
        this.authService = authService;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Autenticación de usuario",
            description = "Autentica con email/password y retorna JWT con permisos dinámicos; ante falla transitoria de authorization-service aplica fallback a permissions vacíos.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Autenticación exitosa", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"timestamp\":\"2026-05-13T10:30:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"email must be a well-formed email address\",\"path\":\"/api/auth/login\"}"))),
                    @ApiResponse(responseCode = "401", description = "Credenciales inválidas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"timestamp\":\"2026-05-13T10:30:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Credenciales inválidas\",\"path\":\"/api/auth/login\"}"))),
                    @ApiResponse(responseCode = "403", description = "Usuario inactivo", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                    @ApiResponse(responseCode = "423", description = "Cuenta bloqueada por exceso de intentos fallidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"timestamp\":\"2026-05-19T10:30:00Z\",\"status\":423,\"error\":\"Locked\",\"message\":\"Acceso temporalmente restringido\",\"path\":\"/api/auth/login\"}"))),
                    @ApiResponse(responseCode = "429", description = "Rate limit excedido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"timestamp\":\"2026-05-19T10:30:00Z\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Demasiadas solicitudes, intente más tarde\",\"path\":\"/api/auth/login\"}")))
            }
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        return ResponseEntity.ok(authService.login(request, clientIp));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovación de sesión mediante refresh token",
            description = "Valida un refresh token válido y emite un nuevo par de access y refresh tokens.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Renovación exitosa", content = @Content(schema = @Schema(implementation = RefreshResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Refresh token inválido o expirado", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
            }
    )
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/register")
    @Operation(
            summary = "Registro de usuario",
            description = "Registra usuario con contraseña segura y asigna permiso inicial REPORT:READ en flujo interno idempotente (ASSIGNED/ALREADY_ASSIGNED) con rollback atómico ante fallo.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Registro exitoso", content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Correo duplicado", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"timestamp\":\"2026-05-13T10:36:00Z\",\"status\":409,\"error\":\"Conflict\",\"message\":\"El correo ya se encuentra registrado\",\"path\":\"/api/auth/register\"}"))),
                    @ApiResponse(responseCode = "503", description = "Falla de integración para permiso inicial", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
            }
    )
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Introspección del token JWT",
            description = "Devuelve la identidad y permisos activos del usuario autenticado extraídos del JWT Bearer. Requiere header Authorization: Bearer <token>.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Identidad y permisos del usuario autenticado",
                            content = @Content(schema = @Schema(implementation = MeResponse.class),
                                    examples = @ExampleObject(value = "{\"username\":\"demo.user@email.com\",\"permissions\":[\"REPORT:READ\"]}"))),
                    @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado",
                            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Acceso denegado",
                            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
            }
    )
    public ResponseEntity<MeResponse> getMe(Authentication authentication) {
        return ResponseEntity.ok(getCurrentUserUseCase.getCurrentUser(authentication.getName()));
    }
}

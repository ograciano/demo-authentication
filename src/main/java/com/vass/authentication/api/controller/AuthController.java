package com.vass.authentication.api.controller;

import com.vass.authentication.api.dto.ApiErrorResponse;
import com.vass.authentication.api.dto.LoginRequest;
import com.vass.authentication.api.dto.LoginResponse;
import com.vass.authentication.api.dto.RegisterRequest;
import com.vass.authentication.api.dto.RegisterResponse;
import com.vass.authentication.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Autenticación de usuario",
            description = "Autentica con email/password y retorna JWT con permisos dinámicos; ante falla transitoria de authorization-service aplica fallback a permissions vacíos.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Autenticación exitosa", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"timestamp\":\"2026-05-13T10:30:00Z\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"email must be a well-formed email address\",\"path\":\"/api/auth/login\"}"))),
                    @ApiResponse(responseCode = "401", description = "Credenciales inválidas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class), examples = @ExampleObject(value = "{\"timestamp\":\"2026-05-13T10:30:00Z\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Credenciales inválidas\",\"path\":\"/api/auth/login\"}"))),
                    @ApiResponse(responseCode = "403", description = "Usuario inactivo", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
            }
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
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
}

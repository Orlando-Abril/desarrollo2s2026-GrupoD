package com.example.demo.controller;

import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.LoginResponse;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.dto.auth.RegisterResponse;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import com.example.demo.service.AuthService;
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
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Registro e inicio de sesión de usuario")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar una cuenta nueva",
            description = "Crea la cuenta, hashea el password, genera la ApiKey inicial (se "
                    + "devuelve una única vez en esta respuesta) y deja el saldo inicial "
                    + "estándar de la plataforma.")
    @ApiResponse(responseCode = "201", description = "Cuenta creada",
            content = @Content(schema = @Schema(implementation = RegisterResponse.class),
                    examples = @ExampleObject(value = "{\"id\":1,\"username\":\"abril\","
                            + "\"email\":\"abril@example.com\",\"balance\":1000.00,"
                            + "\"apiKey\":\"sk_9f3a1c7b2e4d6f8a0c1b3d5e7f9a1c3e\"}")))
    @ApiResponse(responseCode = "400", description = "Datos de registro inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"error\":\"validation_error\","
                            + "\"message\":\"email debe tener un formato válido\"}")))
    @ApiResponse(responseCode = "409", description = "El username o el email ya están registrados",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"error\":\"duplicate_user\","
                            + "\"message\":\"El username ya está registrado\"}")))
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Valida username y password contra el hash almacenado y, si son "
                    + "correctos, devuelve un JWT para operaciones posteriores.")
    @ApiResponse(responseCode = "200", description = "Login exitoso",
            content = @Content(schema = @Schema(implementation = LoginResponse.class),
                    examples = @ExampleObject(value = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\","
                            + "\"tokenType\":\"Bearer\"}")))
    @ApiResponse(responseCode = "400", description = "Datos de login inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"error\":\"validation_error\","
                            + "\"message\":\"password no puede estar vacío\"}")))
    @ApiResponse(responseCode = "401", description = "Username o password inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"error\":\"invalid_credentials\","
                            + "\"message\":\"Username o password inválidos\"}")))
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}

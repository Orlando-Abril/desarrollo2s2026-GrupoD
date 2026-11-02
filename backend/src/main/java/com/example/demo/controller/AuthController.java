package com.example.demo.controller;

import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.LoginResponse;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.dto.auth.RegisterResponse;
import com.example.demo.dto.auth.MeResponse;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import com.example.demo.dto.auth.SessionResponse;
import com.example.demo.service.AuthService;
import com.example.demo.service.RefreshTokenService;
import com.example.demo.service.RefreshTokenService.InvalidRefreshTokenException;
import com.example.demo.service.RefreshTokenService.Renewal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import java.time.Duration;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.headers.Header;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Registro e inicio de sesión de usuario")
public class AuthController {

    /** Cookie con el token de renovación. Solo viaja a /auth, nunca al resto de la API. */
    public static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/auth";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final boolean secureCookie;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService,
                          @Value("${security.refresh.cookie-secure}") boolean secureCookie) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.secureCookie = secureCookie;
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
                    + "correctos, devuelve un JWT para operaciones posteriores. Además deja una "
                    + "cookie HttpOnly refresh_token para renovar la sesión con POST /auth/refresh.")
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
        LoginResponse response = authService.login(request);
        String refreshToken = refreshTokenService.issue(request.username());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, refreshTokenService.lifetime()))
                .body(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar la sesión",
            description = "Canjea la cookie refresh_token por un JWT nuevo sin pedir la contraseña "
                    + "(lo usa el front al recargar la página o cuando el JWT vence). La cookie usada "
                    + "queda revocada y se entrega otra nueva.")
    @ApiResponse(responseCode = "200", description = "Sesión renovada",
            content = @Content(schema = @Schema(implementation = SessionResponse.class),
                    examples = @ExampleObject(value = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9...\","
                            + "\"tokenType\":\"Bearer\",\"username\":\"abril\"}")))
    @ApiResponse(responseCode = "401", description = "No hay cookie, o venció, o ya se usó",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"error\":\"session_expired\","
                            + "\"message\":\"La sesión venció. Volvé a ingresar.\"}")))
    public ResponseEntity<Object> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        try {
            Renewal renewal = refreshTokenService.renew(refreshToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie(renewal.refreshToken(), refreshTokenService.lifetime()))
                    .body(new SessionResponse(renewal.accessToken(), renewal.username()));
        } catch (InvalidRefreshTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                    .body(new ErrorResponse("session_expired", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión",
            description = "Revoca el token de renovación de la cookie y la borra del navegador. "
                    + "Responde 204 aunque no haya cookie.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                .build();
    }

    // HttpOnly: JavaScript no la puede leer, así un script inyectado no se la roba.
    // SameSite=Strict: el navegador no la manda desde otros sitios, eso cubre CSRF en /auth/refresh.
    // Secure: solo viaja por HTTPS (los navegadores tratan http://localhost como seguro).
    private String refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar la cuenta autenticada",
            description = "Devuelve id, username, email, rol y saldo vigente de la cuenta representada por la credencial.",
            security = {@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponse(responseCode = "200", description = "Cuenta autenticada",
            headers = @Header(name = "X-Correlation-ID", ref = "#/components/headers/CorrelationId"),
            content = @Content(schema = @Schema(implementation = MeResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credencial ausente, inválida o sin cuenta asociada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.me(authentication.getName()));
    }
}

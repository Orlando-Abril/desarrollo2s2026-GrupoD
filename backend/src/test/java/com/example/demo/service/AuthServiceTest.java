package com.example.demo.service;

import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.LoginResponse;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.dto.auth.RegisterResponse;
import com.example.demo.exception.DuplicateUserException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.model.ApiKey;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.ApiKeyHasher;
import com.example.demo.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService() {
        return new AuthService(userRepository, passwordEncoder, jwtUtil);
    }

    @Test
    void registerConDatosValidosDeberiaHashearElPasswordYGenerarUnaApiKey() {
        when(userRepository.existsByUsername("abril")).thenReturn(false);
        when(userRepository.existsByEmail("abril@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash-bcrypt-simulado");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest("abril", "abril@example.com", "unPasswordSeguro123");
        RegisterResponse response = authService().register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getPasswordHash()).isEqualTo("hash-bcrypt-simulado");
        assertThat(savedUser.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.balance()).isEqualByComparingTo("1000.00");
        assertThat(response.apiKey()).isNotBlank();
        assertThat(savedUser.getApiKey()).isNotNull();
        assertThat(savedUser.getApiKey().getOwner()).isEqualTo(savedUser);
        assertThat(savedUser.getApiKey().getKeyHash()).isEqualTo(ApiKeyHasher.sha256Hex(response.apiKey()));
        assertThat(savedUser.getApiKey().getKeyPrefix()).startsWith("sk_").hasSize(11);
    }

    @Test
    void registerConUsernameYaRegistradoDeberiaLanzarDuplicateUserException() {
        when(userRepository.existsByUsername("abril")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("abril", "nuevo@example.com", "unPasswordSeguro123");

        assertThatThrownBy(() -> authService().register(request))
                .isInstanceOf(DuplicateUserException.class);
    }

    @Test
    void registerConEmailYaRegistradoDeberiaLanzarDuplicateUserException() {
        when(userRepository.existsByUsername("nuevo")).thenReturn(false);
        when(userRepository.existsByEmail("abril@example.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("nuevo", "abril@example.com", "unPasswordSeguro123");

        assertThatThrownBy(() -> authService().register(request))
                .isInstanceOf(DuplicateUserException.class);
    }

    @Test
    void registerConUsernameEnMayusculasDeberiaNormalizarloAMinusculas() {
        when(userRepository.existsByUsername("abril")).thenReturn(false);
        when(userRepository.existsByEmail("abril@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash-bcrypt-simulado");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest("Abril", "abril@example.com", "unPasswordSeguro123");
        RegisterResponse response = authService().register(request);

        assertThat(response.username()).isEqualTo("abril");
        verify(userRepository).existsByUsername("abril");
    }

    @Test
    void loginConCredencialesCorrectasDeberiaDevolverElTokenDeJwtUtil() {
        User user = User.builder().username("abril").passwordHash("hash-bcrypt-simulado").build();
        when(userRepository.findByUsername("abril")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("unPasswordSeguro123", "hash-bcrypt-simulado")).thenReturn(true);
        when(jwtUtil.generateToken("abril")).thenReturn("token-jwt-simulado");

        LoginResponse response = authService().login(new LoginRequest("abril", "unPasswordSeguro123"));

        assertThat(response.token()).isEqualTo("token-jwt-simulado");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginConUsernameEnMayusculasDeberiaEncontrarLaCuentaGuardadaEnMinusculas() {
        User user = User.builder().username("abril").passwordHash("hash-bcrypt-simulado").build();
        when(userRepository.findByUsername("abril")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtil.generateToken("abril")).thenReturn("token-jwt-simulado");

        authService().login(new LoginRequest("Abril", "unPasswordSeguro123"));

        verify(userRepository).findByUsername("abril");
    }

    @Test
    void loginConPasswordIncorrectoDeberiaLanzarInvalidCredentialsException() {
        User user = User.builder().username("abril").passwordHash("hash-bcrypt-simulado").build();
        when(userRepository.findByUsername("abril")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService().login(new LoginRequest("abril", "incorrecto")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginConUsernameInexistenteDeberiaLanzarInvalidCredentialsExceptionConElMismoMensajeQuePasswordIncorrecto() {
        User user = User.builder().username("abril").passwordHash("hash-bcrypt-simulado").build();
        when(userRepository.findByUsername("abril")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        Throwable porPasswordIncorrecto = catchThrowable(
                () -> authService().login(new LoginRequest("abril", "incorrecto")));
        Throwable porUsernameInexistente = catchThrowable(
                () -> authService().login(new LoginRequest("noexiste", "cualquiera")));

        assertThat(porPasswordIncorrecto).isInstanceOf(InvalidCredentialsException.class);
        assertThat(porUsernameInexistente).isInstanceOf(InvalidCredentialsException.class);
        assertThat(porUsernameInexistente.getMessage()).isEqualTo(porPasswordIncorrecto.getMessage());
    }

    @Test
    void loginConUsernameInexistenteDeberiaInvocarPasswordEncoderMatchesIgualQueConUsernameExistente() {
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService().login(new LoginRequest("noexiste", "cualquiera")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void loginConCredencialesCorrectasNoDeberiaModificarLaApiKeyDelUsuario() {
        ApiKey apiKey = ApiKey.builder().keyHash("hash-apikey-original").keyPrefix("sk_abcd1234").build();
        User user = User.builder()
                .username("abril")
                .passwordHash("hash-bcrypt-simulado")
                .apiKey(apiKey)
                .build();
        when(userRepository.findByUsername("abril")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtil.generateToken("abril")).thenReturn("token-jwt-simulado");

        authService().login(new LoginRequest("abril", "unPasswordSeguro123"));

        assertThat(user.getApiKey().getKeyHash()).isEqualTo("hash-apikey-original");
        verify(userRepository, never()).save(any());
    }
}

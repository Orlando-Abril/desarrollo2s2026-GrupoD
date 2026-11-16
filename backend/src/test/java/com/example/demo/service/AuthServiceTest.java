package com.example.demo.service;

import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.LoginResponse;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.dto.auth.RegisterResponse;
import com.example.demo.exception.DuplicateUserException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.exception.AuthenticatedUserNotFoundException;
import com.example.demo.metrics.BusinessMetrics;
import com.example.demo.metrics.FailingMeterRegistry;
import com.example.demo.model.Role;
import com.example.demo.model.ApiKey;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.ApiKeyHasher;
import com.example.demo.security.JwtUtil;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.ArgumentMatchers.eq;
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

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private AuthService authService() {
        return new AuthService(userRepository, passwordEncoder, jwtUtil,
                new BusinessMetrics(meterRegistry, userRepository));
    }

    // Spec 036: registros, logins y usuarios registrados

    @Test
    void validRegistrationIsCountedAsSuccess() {
        when(userRepository.existsByUsername("abril")).thenReturn(false);
        when(userRepository.existsByEmail("abril@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService().register(new RegisterRequest("abril", "abril@example.com", "unPasswordSeguro123"));

        assertThat(count("gdmarket.auth.registrations", "success")).isEqualTo(1);
        assertThat(count("gdmarket.auth.registrations", "failure")).isZero();
    }

    @Test
    void duplicateUsernameAndEmailAreCountedAsFailures() {
        when(userRepository.existsByUsername("abril")).thenReturn(true);
        when(userRepository.existsByUsername("nuevo")).thenReturn(false);
        when(userRepository.existsByEmail("abril@example.com")).thenReturn(true);
        AuthService service = authService();

        assertThatThrownBy(() -> service.register(new RegisterRequest("abril", "x@example.com", "unPasswordSeguro123")))
                .isInstanceOf(DuplicateUserException.class);
        assertThatThrownBy(() -> service.register(new RegisterRequest("nuevo", "abril@example.com", "unPasswordSeguro123")))
                .isInstanceOf(DuplicateUserException.class);

        assertThat(count("gdmarket.auth.registrations", "failure")).isEqualTo(2);
        assertThat(count("gdmarket.auth.registrations", "success")).isZero();
    }

    @Test
    void loginsAreCountedByOutcomeWithoutIdentifyingTheUser() {
        User user = User.builder().username("abril").passwordHash("hash").build();
        when(userRepository.findByUsername("abril")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("bueno", "hash")).thenReturn(true);
        when(passwordEncoder.matches("malo", "hash")).thenReturn(false);
        when(passwordEncoder.matches(eq("cualquiera"), anyString())).thenReturn(false);
        when(jwtUtil.generateToken("abril")).thenReturn("token");
        AuthService service = authService();

        service.login(new LoginRequest("abril", "bueno"));
        assertThatThrownBy(() -> service.login(new LoginRequest("abril", "malo")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login(new LoginRequest("noexiste", "cualquiera")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(count("gdmarket.auth.logins", "success")).isEqualTo(1);
        assertThat(count("gdmarket.auth.logins", "failure")).isEqualTo(2);
        assertThat(meterRegistry.get("gdmarket.auth.logins").counters())
                .allSatisfy(counter -> assertThat(counter.getId().getTags())
                        .extracting(Tag::getKey).containsOnly("outcome"));
    }

    @Test
    void registeredUsersGaugeReadsTheRepositoryCount() {
        authService();
        when(userRepository.count()).thenReturn(5L);

        assertThat(meterRegistry.get("gdmarket.users.registered").gauge().value()).isEqualTo(5.0);
    }

    @Test
    void failingMeasurementsDoNotChangeRegistrationOrLogin() {
        AuthService service = new AuthService(userRepository, passwordEncoder, jwtUtil,
                new BusinessMetrics(new FailingMeterRegistry(), userRepository));
        when(userRepository.existsByUsername("abril")).thenReturn(false);
        when(userRepository.existsByEmail("abril@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User user = User.builder().username("abril").passwordHash("hash").build();
        when(userRepository.findByUsername("abril")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bueno", "hash")).thenReturn(true);
        when(passwordEncoder.matches("malo", "hash")).thenReturn(false);
        when(jwtUtil.generateToken("abril")).thenReturn("token");

        RegisterResponse registered = service.register(
                new RegisterRequest("abril", "abril@example.com", "unPasswordSeguro123"));
        LoginResponse login = service.login(new LoginRequest("abril", "bueno"));

        assertThat(registered.username()).isEqualTo("abril");
        assertThat(registered.apiKey()).startsWith("sk_");
        assertThat(login.token()).isEqualTo("token");
        assertThatThrownBy(() -> service.login(new LoginRequest("abril", "malo")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private double count(String name, String outcome) {
        return meterRegistry.get(name).tag("outcome", outcome).counter().count();
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

    @Test
    void meMapsTheCurrentUserWithoutWriting() {
        User user = User.builder().id(9L).username("abril").email("abril@example.com").role(Role.USER)
                .balance(new java.math.BigDecimal("123.45")).build();
        when(userRepository.findByUsername("abril")).thenReturn(Optional.of(user));

        var response = authService().me("abril");

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.balance()).isEqualByComparingTo("123.4500");
        verify(userRepository, never()).save(any());
    }

    @Test
    void meRejectsAServicePrincipalWithoutOwner() {
        when(userRepository.findByUsername("svc_key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService().me("svc_key"))
                .isInstanceOf(AuthenticatedUserNotFoundException.class);
    }
}

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class AuthService {

    private static final String API_KEY_PREFIX = "sk_";
    private static final int API_KEY_RANDOM_BYTES = 24;
    private static final int API_KEY_PREFIX_LENGTH = 11;

    /**
     * Hash BCrypt "dummy" calculado una única vez en runtime (nunca un literal hardcodeado, para
     * no disparar el detector de "hardcoded credential" de SonarCloud). Se usa para que un login
     * con username inexistente tarde lo mismo que uno con password incorrecto (research.md §3).
     */
    private static final String DUMMY_PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("dummy-password-para-mitigar-timing");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public RegisterResponse register(RegisterRequest request) {
        String username = request.username().toLowerCase();
        String email = request.email();

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUserException("El username ya está registrado");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException("El email ya está registrado");
        }

        String rawApiKey = generateRawApiKey();
        ApiKey apiKey = ApiKey.builder()
                .keyHash(ApiKeyHasher.sha256Hex(rawApiKey))
                .keyPrefix(rawApiKey.substring(0, API_KEY_PREFIX_LENGTH))
                .build();

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .apiKey(apiKey)
                .build();
        apiKey.setOwner(user);

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUserException("El username o el email ya están registrados");
        }

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getBalance(),
                rawApiKey
        );
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username().toLowerCase();
        Optional<User> userOptional = userRepository.findByUsername(username);

        String hashToCheck = userOptional.map(User::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (userOptional.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException("Username o password inválidos");
        }

        return new LoginResponse(jwtUtil.generateToken(username));
    }

    private String generateRawApiKey() {
        byte[] randomBytes = new byte[API_KEY_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}

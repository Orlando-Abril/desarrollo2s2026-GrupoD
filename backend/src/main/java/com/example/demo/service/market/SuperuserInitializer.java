package com.example.demo.service.market;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActor;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.audit.TransactionAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Al arrancar garantiza un único superusuario ADMIN y emite los tokens que falten (spec 026, US1 y US2).
 * Se deshabilita con {@code market.superuser.enabled=false}; se lee con {@code @Value} y no con
 * {@code @ConditionalOnProperty} porque la propiedad viene de un {@code @PropertySource}, que puede
 * cargarse después de evaluar las condiciones.
 */
@Component
public class SuperuserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperuserInitializer.class);

    private final boolean enabled;
    private final SuperuserAccount account;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionAuditService auditService;
    private final TokenIssuanceService tokenIssuanceService;
    private final TransactionOperations transactionOperations;

    public SuperuserInitializer(@Value("${market.superuser.enabled:true}") boolean enabled,
                                SuperuserAccount account, UserRepository userRepository,
                                PasswordEncoder passwordEncoder, TransactionAuditService auditService,
                                TokenIssuanceService tokenIssuanceService,
                                TransactionOperations transactionOperations) {
        this.enabled = enabled;
        this.account = account;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.tokenIssuanceService = tokenIssuanceService;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("market_superuser_disabled");
            return;
        }
        User superuser;
        try {
            superuser = transactionOperations.execute(status -> ensureSuperuser());
        } catch (DataIntegrityViolationException ex) {
            // Otra instancia lo creó a la vez: la transacción anterior ya se revirtió, se relee en una nueva.
            superuser = transactionOperations.execute(status -> existingAdmin(ex));
        }
        if (account.usesDefaultPassword()) {
            log.warn("market_superuser_default_password username={}", account.username());
        }
        log.info("market_superuser_ready id={}", superuser.getId());
        tokenIssuanceService.issueMissing();
    }

    /** Requiere una transacción activa: la auditoría corre con {@code MANDATORY}. */
    public User ensureSuperuser() {
        User existing = userRepository.findByUsername(account.username()).orElse(null);
        if (existing != null) {
            if (existing.getRole() != Role.ADMIN) {
                throw new IllegalStateException("superuser_username_taken");
            }
            return existing;
        }
        if (userRepository.existsByEmail(account.email())) {
            throw new IllegalStateException("superuser_email_taken");
        }
        User created = userRepository.save(User.builder()
                .username(account.username())
                .email(account.email())
                .passwordHash(passwordEncoder.encode(account.password()))
                .role(Role.ADMIN)
                .build());
        auditService.audit(AuditActor.system(), AuditAction.SUPERUSER_CREATED, "USER", created.getId(),
                null, UserSnapshot.of(created));
        log.info("market_superuser_created id={}", created.getId());
        return created;
    }

    private User existingAdmin(DataIntegrityViolationException cause) {
        return userRepository.findByUsername(account.username())
                .filter(user -> user.getRole() == Role.ADMIN)
                .orElseThrow(() -> cause);
    }
}

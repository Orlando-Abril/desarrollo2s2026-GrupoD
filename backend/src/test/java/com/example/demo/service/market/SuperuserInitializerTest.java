package com.example.demo.service.market;

import com.example.demo.model.Player;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActorType;
import com.example.demo.model.market.AuditLog;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.AuditLogRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@UnitTestProfile
@TestPropertySource(properties = "market.superuser.enabled=true")
@ExtendWith(OutputCaptureExtension.class)
class SuperuserInitializerTest {

    @Autowired private SuperuserInitializer initializer;
    @Autowired private UserRepository userRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    // run(null) se llama dentro de cada caso, después de preparar su precondición.
    @BeforeEach
    @AfterEach
    void clean() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void createsASingleAdminWithTheDefaultConfiguration() {
        initializer.run(null);

        User superuser = userRepository.findByUsername("superuser").orElseThrow();
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(superuser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(superuser.getEmail()).isEqualTo("superuser@futbol-market.local");
        assertThat(passwordEncoder.matches("Superuser.2026", superuser.getPasswordHash())).isTrue();
    }

    @Test
    void repeatedInitializationsDoNotDuplicateOrChangeIt() {
        initializer.run(null);
        User first = userRepository.findByUsername("superuser").orElseThrow();

        for (int i = 0; i < 5; i++) {
            initializer.run(null);
        }

        User after = userRepository.findByUsername("superuser").orElseThrow();
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(after.getPasswordHash()).isEqualTo(first.getPasswordHash());
        assertThat(after.getEmail()).isEqualTo(first.getEmail());
    }

    @Test
    void auditsTheCreationWithoutThePassword() {
        initializer.run(null);
        initializer.run(null);

        String hash = userRepository.findByUsername("superuser").orElseThrow().getPasswordHash();
        List<AuditLog> logs = auditLogRepository.findByActionOrderByIdAsc(AuditAction.SUPERUSER_CREATED);
        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.getActorType()).isEqualTo(AuditActorType.SYSTEM);
            assertThat(log.getBeforeState()).isNull();
            assertThat(log.getAfterState())
                    .contains("superuser")
                    .doesNotContain("passwordHash", "password_hash", hash);
        });
    }

    @Test
    void failsWithoutChangesWhenTheUsernameBelongsToANonAdmin() {
        userRepository.save(User.builder().username("superuser").email("someone@local")
                .passwordHash("x").role(Role.USER).build());

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("superuser_username_taken");
        assertThat(userRepository.findByUsername("superuser").orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void failsWithoutCreatingWhenTheEmailIsTaken() {
        userRepository.save(User.builder().username("other").email("superuser@futbol-market.local")
                .passwordHash("x").build());

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("superuser_email_taken");
        assertThat(userRepository.findByUsername("superuser")).isEmpty();
    }

    @Test
    void warnsWhenUsingTheDefaultPassword(CapturedOutput output) {
        initializer.run(null);

        assertThat(output.getOut()).contains("market_superuser_default_password").doesNotContain("Superuser.2026");
    }

    @Test
    void issuesTokensOfExistingPlayersOnce() {
        Player first = MarketTestData.player(playerRepository);
        Player second = MarketTestData.player(playerRepository);

        initializer.run(null);
        initializer.run(null);

        Long superuserId = userRepository.findByUsername("superuser").orElseThrow().getId();
        for (Player player : List.of(first, second)) {
            assertThat(holdingRepository.findByUserIdAndPlayerId(superuserId, player.getId()))
                    .get().extracting("quantity").isEqualTo(100);
            assertThat(holdingRepository.sumQuantityByPlayerId(player.getId())).isEqualTo(100);
        }
    }
}

package com.example.demo.service.market;

import com.example.demo.model.Player;
import com.example.demo.model.market.AuditAction;
import com.example.demo.model.market.AuditActorType;
import com.example.demo.model.market.AuditLog;
import com.example.demo.model.market.Holding;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.market.AuditLogRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@UnitTestProfile
@TestPropertySource(properties = "market.superuser.enabled=true")
class TokenIssuanceServiceTest {

    @Autowired private TokenIssuanceService issuanceService;
    @Autowired private SuperuserInitializer initializer;
    @Autowired private SuperuserAccount superuserAccount;
    @Autowired private PlayerRepository playerRepository;
    @MockitoSpyBean private HoldingRepository holdingRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private TransactionOperations transactionOperations;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long superuserId;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        // También emite los jugadores que hayan dejado otros tests: cada caso verifica sólo los suyos.
        initializer.run(null);
        superuserId = superuserAccount.id();
    }

    @AfterEach
    void clean() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void issuesOneHundredTokensPerPlayer() {
        List<Player> players = List.of(MarketTestData.player(playerRepository),
                MarketTestData.player(playerRepository), MarketTestData.player(playerRepository));

        IssuanceResult result = issuanceService.issueMissing();

        assertThat(result).isEqualTo(new IssuanceResult(3, 0));
        for (Player player : players) {
            Holding holding = holdingRepository.findByUserIdAndPlayerId(superuserId, player.getId()).orElseThrow();
            assertThat(holding.getQuantity()).isEqualTo(100);
            assertThat(holding.getReservedQuantity()).isZero();
        }
    }

    @Test
    void aSecondIssuanceIssuesNothing() {
        Player player = MarketTestData.player(playerRepository);
        issuanceService.issueMissing();

        assertThat(issuanceService.issueMissing()).isEqualTo(new IssuanceResult(0, 0));
        assertThat(holdingRepository.sumQuantityByPlayerId(player.getId())).isEqualTo(100);
    }

    @Test
    void neverRefillsTheSuperuserAfterItSoldItsTokens() {
        Player player = MarketTestData.player(playerRepository);
        issuanceService.issueMissing();
        transactionOperations.executeWithoutResult(status -> {
            Holding holding = holdingRepository.findByUserIdAndPlayerId(superuserId, player.getId()).orElseThrow();
            holding.debit(100, Instant.now());
            holdingRepository.save(holding);
        });

        issuanceService.issueMissing();

        assertThat(holdingRepository.findByUserIdAndPlayerId(superuserId, player.getId()))
                .get().extracting(Holding::getQuantity).isEqualTo(0);
    }

    @Test
    void auditsEachIssuance() {
        Player player = MarketTestData.player(playerRepository);
        issuanceService.issueMissing();

        Long holdingId = holdingRepository.findByUserIdAndPlayerId(superuserId, player.getId()).orElseThrow().getId();
        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(
                "HOLDING", holdingId.toString());
        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.getAction()).isEqualTo(AuditAction.TOKENS_ISSUED);
            assertThat(log.getActorType()).isEqualTo(AuditActorType.SYSTEM);
            assertThat(log.getBeforeState()).isNull();
            assertThat(log.getAfterState()).contains("\"quantity\":100");
        });
    }

    @Test
    void aFailingPlayerDoesNotStopTheOthersAndIsIssuedLater() {
        List<Player> players = List.of(MarketTestData.player(playerRepository),
                MarketTestData.player(playerRepository), MarketTestData.player(playerRepository));
        Long failingPlayerId = players.get(1).getId();
        doThrow(new IllegalStateException("simulated failure")).when(holdingRepository)
                .save(argThat((Holding holding) -> failingPlayerId.equals(holding.getPlayer().getId())));

        IssuanceResult first = issuanceService.issueMissing();
        reset(holdingRepository);

        assertThat(first).isEqualTo(new IssuanceResult(2, 1));
        assertThat(holdingRepository.findByUserIdAndPlayerId(superuserId, failingPlayerId)).isEmpty();
        assertThat(issuanceService.issueMissing()).isEqualTo(new IssuanceResult(1, 0));
        players.forEach(player -> assertThat(holdingRepository.sumQuantityByPlayerId(player.getId())).isEqualTo(100));
    }

    @Test
    void requiresTheSuperuser() {
        MarketTestData.clean(jdbcTemplate);

        assertThatThrownBy(() -> issuanceService.issueMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(SuperuserAccount.MISSING);
    }
}

package com.example.demo.service.market;

import com.example.demo.repository.UserRepository;
import com.example.demo.service.valuation.QuoteRunCompletedEvent;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/** Sin {@code @TestPropertySource}: toma {@code market.superuser.enabled=false} de {@code src/test/resources}. */
@SpringBootTest
@UnitTestProfile
@ExtendWith(OutputCaptureExtension.class)
class SuperuserInitializerDisabledTest {

    @Autowired private SuperuserInitializer initializer;
    @Autowired private UserRepository userRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void doesNothingWhenDisabled(CapturedOutput output) {
        initializer.run(null);

        assertThat(output.getOut()).contains("market_superuser_disabled");
        assertThat(userRepository.findByUsername("superuser")).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM holdings", Long.class)).isZero();
    }

    @Test
    void theListenerOnlyLogsWhenThereIsNoSuperuser(CapturedOutput output) {
        assertThatNoException().isThrownBy(() -> eventPublisher.publishEvent(new QuoteRunCompletedEvent(UUID.randomUUID())));

        assertThat(output.getOut()).contains("market_issuance_skipped reason=superuser_missing");
    }
}

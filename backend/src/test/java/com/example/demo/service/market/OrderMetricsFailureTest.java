package com.example.demo.service.market;

import com.example.demo.dto.market.OrderRequest;
import com.example.demo.exception.InsufficientBalanceException;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.metrics.BusinessMetrics;
import com.example.demo.metrics.FailingMeterRegistry;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.OrderStatus;
import com.example.demo.model.market.OrderType;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 036, SC-003: con todas las mediciones fallando, una orden da el mismo resultado que con la medición sana
 * (los valores esperados son los de {@code OrderServiceBuyTest}), incluido lo que se mide al confirmar.
 */
@SpringBootTest
@UnitTestProfile
class OrderMetricsFailureTest {

    @TestConfiguration
    static class FailingMetricsConfig {
        @Bean
        @Primary
        BusinessMetrics failingBusinessMetrics(UserRepository userRepository) {
            return new BusinessMetrics(new FailingMeterRegistry(), userRepository);
        }
    }

    @Autowired private OrderService orderService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private MarketSeed seed;
    private Player player;
    private User superuser;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        seed = MarketSeed.from(context);
        player = seed.player("1.2000");
        superuser = seed.superuser();
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.2000");
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void aCrossingBuyKeepsItsResultWhenMeasuringFails() {
        User buyer = seed.user("failing-" + UUID.randomUUID(), "1000.0000");

        OrderResult result = orderService.placeBuy(buyer.getUsername(), market(10));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.getQuantity()).isEqualTo(10);
            assertThat(trade.getTotal()).isEqualByComparingTo("12.0000");
        });
        assertThat(jdbcTemplate.queryForObject("select count(*) from trades", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select balance from users where id = ?", BigDecimal.class,
                buyer.getId())).isEqualByComparingTo("988.0000");
    }

    @Test
    void aRejectedBuyStillThrowsTheSameExceptionWhenMeasuringFails() {
        User poor = seed.user("failing-" + UUID.randomUUID(), "1.0000");

        assertThatThrownBy(() -> orderService.placeBuy(poor.getUsername(), market(10)))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    private OrderRequest market(int quantity) {
        return new OrderRequest(player.getId(), quantity, OrderType.MARKET, null);
    }
}

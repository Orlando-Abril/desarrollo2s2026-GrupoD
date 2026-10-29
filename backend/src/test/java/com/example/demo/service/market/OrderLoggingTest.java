package com.example.demo.service.market;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.demo.exception.MarketExceptionHandler;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.MarketMockMvcTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Logs estructurados del mercado con el correlation id del pedido y sin credenciales (spec 027, FR-030). */
@MarketMockMvcTest
class OrderLoggingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationContext context;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final List<Logger> loggers = List.of(
            (Logger) LoggerFactory.getLogger(OrderService.class),
            (Logger) LoggerFactory.getLogger(MarketExceptionHandler.class));

    private Player player;
    private String apiKey;
    private String poorKey;

    @BeforeEach
    void setUp() {
        MarketTestData.clean(jdbcTemplate);
        MarketSeed seed = MarketSeed.from(context);
        player = seed.player("1.0000");
        User superuser = seed.superuser();
        seed.holding(superuser, player, 100);
        seed.sell(superuser, player, 100, "1.0000");
        apiKey = AuthFixtures.apiKey(apiKeyRepository, seed.user("rich-" + UUID.randomUUID(), "1000.0000"));
        poorKey = AuthFixtures.apiKey(apiKeyRepository, seed.user("poor-" + UUID.randomUUID(), "1.0000"));
        appender.start();
        loggers.forEach(logger -> logger.addAppender(appender));
    }

    @AfterEach
    void tearDown() {
        loggers.forEach(logger -> logger.detachAppender(appender));
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void aBuyLogsTheOrderAndTheTradeWithTheRequestCorrelationId() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(post("/orders/buy").header("X-API-KEY", apiKey)
                        .header(CorrelationIdFilter.HEADER, correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(market(2)))
                .andExpect(status().isCreated());

        assertThat(messages()).anyMatch(message -> message.startsWith("market_trade_executed tradeId="))
                .anyMatch(message -> message.startsWith("market_order_placed orderId=") && message.contains("status=FILLED"));
        assertThat(appender.list).allSatisfy(event ->
                assertThat(event.getMDCPropertyMap()).containsEntry(CorrelationIdFilter.MDC_KEY, correlationId));
        assertThat(messages()).noneMatch(message -> message.contains(apiKey));
    }

    @Test
    void aRejectionLogsItsReasonWithoutCredentials() throws Exception {
        mockMvc.perform(post("/orders/buy").header("X-API-KEY", poorKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(market(5)))
                .andExpect(status().isUnprocessableContent());

        assertThat(messages()).contains("market_order_rejected status=422 reason=insufficient_balance");
        assertThat(messages()).noneMatch(message -> message.contains(poorKey) || message.contains("not-used"));
    }

    private String market(int quantity) {
        return "{\"playerId\":%d,\"quantity\":%d,\"type\":\"MARKET\"}".formatted(player.getId(), quantity);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}

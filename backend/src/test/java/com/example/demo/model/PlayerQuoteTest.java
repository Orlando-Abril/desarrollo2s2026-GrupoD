package com.example.demo.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerQuoteTest {

    private static final Instant CALCULATED_AT = Instant.parse("2026-10-05T12:00:00Z");
    private static final UUID RUN_ID = UUID.fromString("7c1d2a8e-0b7f-4f0e-9a51-2f3c4d5e6f70");

    @Test
    void builderExposesEveryField() {
        Player player = Player.builder().id(7L).build();

        PlayerQuote quote = PlayerQuote.builder()
                .player(player)
                .strategyCode("OFFICIAL")
                .strategyVersion(1)
                .value(new BigDecimal("1.2345"))
                .score(new BigDecimal("0.4321"))
                .status(QuoteStatus.CALCULATED)
                .calculatedAt(CALCULATED_AT)
                .runId(RUN_ID)
                .correlationId("corr-1")
                .build();

        assertThat(quote.getPlayer()).isSameAs(player);
        assertThat(quote.getStrategyCode()).isEqualTo("OFFICIAL");
        assertThat(quote.getStrategyVersion()).isEqualTo(1);
        assertThat(quote.getValue()).isEqualByComparingTo("1.2345");
        assertThat(quote.getScore()).isEqualByComparingTo("0.4321");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CALCULATED);
        assertThat(quote.getCalculatedAt()).isEqualTo(CALCULATED_AT);
        assertThat(quote.getRunId()).isEqualTo(RUN_ID);
        assertThat(quote.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void calculatedQuoteRequiresScore() {
        PlayerQuote quote = quote(QuoteStatus.CALCULATED, null);

        assertThatThrownBy(quote::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("score es obligatorio para CALCULATED");
    }

    @ParameterizedTest
    @EnumSource(value = QuoteStatus.class, names = {"NO_STATS", "INSUFFICIENT_HISTORY"})
    void otherStatusesAllowMissingScore(QuoteStatus status) {
        assertThatCode(quote(status, null)::validate).doesNotThrowAnyException();
    }

    @Test
    void exposesNoPublicSetters() {
        assertThat(Arrays.stream(PlayerQuote.class.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    private static PlayerQuote quote(QuoteStatus status, BigDecimal score) {
        return PlayerQuote.builder()
                .player(Player.builder().id(7L).build())
                .strategyCode("OFFICIAL")
                .strategyVersion(1)
                .value(new BigDecimal("1.0000"))
                .score(score)
                .status(status)
                .calculatedAt(CALCULATED_AT)
                .runId(RUN_ID)
                .correlationId("corr-1")
                .build();
    }
}

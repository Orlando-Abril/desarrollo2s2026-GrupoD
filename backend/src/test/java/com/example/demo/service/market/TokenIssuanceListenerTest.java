package com.example.demo.service.market;

import com.example.demo.service.valuation.QuoteRunCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class TokenIssuanceListenerTest {

    private final TokenIssuanceService issuanceService = mock(TokenIssuanceService.class);
    private final TokenIssuanceListener listener = new TokenIssuanceListener(issuanceService);
    private final QuoteRunCompletedEvent event = new QuoteRunCompletedEvent(UUID.randomUUID());

    @Test
    void issuesMissingTokensWhenARunCompletes() {
        listener.onQuoteRunCompleted(event);

        verify(issuanceService).issueMissing();
    }

    @Test
    void neverPropagatesAFailure(CapturedOutput output) {
        when(issuanceService.issueMissing()).thenThrow(new IllegalStateException("boom"));

        assertThatNoException().isThrownBy(() -> listener.onQuoteRunCompleted(event));
        assertThat(output.getOut()).contains("market_issuance_after_quote_run_failed");
    }

    @Test
    void onlyLogsWhenThereIsNoSuperuser(CapturedOutput output) {
        when(issuanceService.issueMissing()).thenThrow(new IllegalStateException(SuperuserAccount.MISSING));

        assertThatNoException().isThrownBy(() -> listener.onQuoteRunCompleted(event));
        assertThat(output.getOut())
                .contains("market_issuance_skipped reason=superuser_missing")
                .doesNotContain("market_issuance_after_quote_run_failed");
    }
}

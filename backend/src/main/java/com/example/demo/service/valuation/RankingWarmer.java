package com.example.demo.service.valuation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RankingWarmer {
    private static final Logger log = LoggerFactory.getLogger(RankingWarmer.class);
    private final QuoteQueryService queries;

    public RankingWarmer(QuoteQueryService queries) { this.queries = queries; }

    @Async
    public void warm() {
        try {
            long before;
            do {
                before = queries.currentRankingGeneration();
                queries.ranking(null, null, null, PageRequest.of(0, 20));
            } while (before != queries.currentRankingGeneration());
        } catch (RuntimeException ex) {
            log.warn("quotes_ranking_warm_failed errorType={}", ex.getClass().getSimpleName());
        }
    }
}

package com.example.demo.service.valuation;

import com.example.demo.dto.quote.OpportunityItemResponse;
import com.example.demo.model.League;
import com.example.demo.model.Position;
import com.example.demo.model.QuoteStatus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Clasificación de oportunidades (spec 031, research R7), sin dependencias de Spring.
 * <p>
 * {@code difference = alternativa − marketValue} (4 decimales) y
 * {@code differencePercent = difference ÷ marketValue × 100}, que se ordena sin redondear y se presenta con 2
 * decimales. Se excluyen los jugadores sin cotización alternativa, con la última cotización alternativa o de la
 * estrategia de mercado distinta de {@code CALCULATED}, sin {@code marketValue} positivo o con diferencia cero.
 */
public final class OpportunityCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private OpportunityCalculator() {
    }

    /**
     * Datos de un jugador para clasificar.
     *
     * @param alternativeValue  nulo si no tiene cotización de la estrategia alternativa
     * @param marketQuoteStatus estado de su última cotización de la estrategia de mercado; nulo si no tiene
     */
    public record Candidate(Long playerId, String name, String team, League league, Set<Position> positions,
                            BigDecimal marketValue, BigDecimal alternativeValue, QuoteStatus alternativeStatus,
                            Instant alternativeCalculatedAt, QuoteStatus marketQuoteStatus) {
    }

    public record Result(List<OpportunityItemResponse> undervalued, List<OpportunityItemResponse> overvalued) {
    }

    private record Ranked(Candidate candidate, BigDecimal difference, BigDecimal percent) {
    }

    public static Result classify(List<Candidate> candidates, int limit) {
        List<Ranked> undervalued = new ArrayList<>();
        List<Ranked> overvalued = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!eligible(candidate)) {
                continue;
            }
            BigDecimal difference = candidate.alternativeValue().subtract(candidate.marketValue());
            int sign = difference.signum();
            if (sign == 0) {
                continue;
            }
            BigDecimal percent = difference.divide(candidate.marketValue(), MathContext.DECIMAL128).multiply(HUNDRED);
            (sign > 0 ? undervalued : overvalued).add(new Ranked(candidate, difference, percent));
        }
        return new Result(top(undervalued, limit), top(overvalued, limit));
    }

    private static boolean eligible(Candidate candidate) {
        return candidate.alternativeValue() != null
                && candidate.alternativeStatus() == QuoteStatus.CALCULATED
                && candidate.marketQuoteStatus() == QuoteStatus.CALCULATED
                && candidate.marketValue() != null
                && candidate.marketValue().signum() > 0;
    }

    private static List<OpportunityItemResponse> top(List<Ranked> ranked, int limit) {
        return ranked.stream()
                .sorted(Comparator.comparing((Ranked r) -> r.percent().abs()).reversed()
                        .thenComparing(r -> r.candidate().name())
                        .thenComparing(r -> r.candidate().playerId()))
                .limit(limit)
                .map(OpportunityCalculator::toItem)
                .toList();
    }

    private static OpportunityItemResponse toItem(Ranked ranked) {
        Candidate candidate = ranked.candidate();
        List<Position> positions = candidate.positions() == null ? List.of()
                : candidate.positions().stream().sorted().toList();
        return new OpportunityItemResponse(candidate.playerId(), candidate.name(), candidate.team(),
                candidate.league(), positions, scale(candidate.marketValue(), 4),
                scale(candidate.alternativeValue(), 4), candidate.alternativeCalculatedAt(),
                scale(ranked.difference(), 4), scale(ranked.percent(), 2));
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_EVEN);
    }
}

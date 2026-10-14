package com.example.demo.repository;

import com.example.demo.model.PlayerQuote;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Historial de cotizaciones. Extiende {@link Repository} y no {@code JpaRepository} para que no exista
 * ninguna operación de borrado: una cotización registrada es inmutable (spec 013, FR-005).
 * Las consultas filtran por código de estrategia e incluyen todas sus versiones; ante empate en
 * {@code calculatedAt} gana el {@code id} mayor, es decir, la registrada más tarde.
 */
public interface PlayerQuoteRepository extends Repository<PlayerQuote, Long> {

    PlayerQuote save(PlayerQuote quote);

    <S extends PlayerQuote> List<S> saveAll(Iterable<S> quotes);

    Optional<PlayerQuote> findById(Long id);

    /** Cotización actual de un jugador para una estrategia. */
    Optional<PlayerQuote> findFirstByPlayerIdAndStrategyCodeOrderByCalculatedAtDescIdDesc(Long playerId,
                                                                                         String strategyCode);

    /** Historial en {@code [from, to]}, inclusivo en ambos extremos; vacío si {@code from > to}. */
    List<PlayerQuote> findByPlayerIdAndStrategyCodeAndCalculatedAtBetweenOrderByCalculatedAtAscIdAsc(
            Long playerId, String strategyCode, Instant from, Instant to);

    Optional<PlayerQuote> findFirstByPlayerIdAndStrategyCodeAndCalculatedAtLessThanEqualOrderByCalculatedAtDescIdDesc(
            Long playerId, String strategyCode, Instant at);

    Optional<PlayerQuote> findFirstByPlayerIdAndStrategyCodeAndCalculatedAtLessThanOrderByCalculatedAtDescIdDesc(
            Long playerId, String strategyCode, Instant before);

    /** Cotización vigente en un instante exacto: la última calculada en o antes de {@code at}. */
    default Optional<PlayerQuote> findEffectiveAt(Long playerId, String strategyCode, Instant at) {
        return findFirstByPlayerIdAndStrategyCodeAndCalculatedAtLessThanEqualOrderByCalculatedAtDescIdDesc(
                playerId, strategyCode, at);
    }

    /**
     * Cotización vigente al fin del día UTC. Usa una frontera exclusiva (inicio del día siguiente) para no
     * depender de la precisión del motor: PostgreSQL redondearía {@code 23:59:59.999999999} al día siguiente.
     */
    default Optional<PlayerQuote> findEffectiveOn(Long playerId, String strategyCode, LocalDate date) {
        return findFirstByPlayerIdAndStrategyCodeAndCalculatedAtLessThanOrderByCalculatedAtDescIdDesc(
                playerId, strategyCode, date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /** Última cotización de cada jugador que tenga al menos una para la estrategia. */
    @Query("""
            select q from PlayerQuote q
            where q.strategyCode = :code
              and not exists (select q2.id from PlayerQuote q2
                              where q2.player = q.player and q2.strategyCode = q.strategyCode
                                and (q2.calculatedAt > q.calculatedAt
                                     or (q2.calculatedAt = q.calculatedAt and q2.id > q.id)))
            """)
    List<PlayerQuote> findLatestForAllPlayers(@Param("code") String strategyCode);
}

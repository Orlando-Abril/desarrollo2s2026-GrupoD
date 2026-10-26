package com.example.demo.repository;

import com.example.demo.model.PlayerQuote;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
            with quote_history as (
                select q.player_id, q.strategy_code, q.strategy_version, q.quote_value,
                       row_number() over (partition by q.player_id, q.strategy_code order by q.calculated_at desc, q.id desc) as quote_order,
                       lead(q.quote_value) over (partition by q.player_id, q.strategy_code order by q.calculated_at desc, q.id desc) as previous_value
                from player_quotes q where q.strategy_code = :strategy
            ), eligible as (
                select p.id as player_id, p.full_name, p.team, p.league, q.quote_value as current_value,
                       q.previous_value, q.strategy_code, q.strategy_version
                from quote_history q join players p on p.id = q.player_id
                where q.quote_order = 1
                  and (cast(:league as varchar) is null or cast(p.league as varchar) = cast(:league as varchar))
                  and (cast(:position as varchar) is null or exists (select 1 from player_positions pp
                       where pp.player_id = p.id and cast(pp.position as varchar) = cast(:position as varchar)))
            ), ranked as (
                select row_number() over (order by current_value desc, player_id asc) as ranking_position, eligible.* from eligible
            )
            select ranking_position as rank, player_id as "playerId", full_name as name, team, league,
                   current_value as "currentValue", previous_value as "previousValue",
                   strategy_code as strategy, strategy_version as version
            from ranked order by current_value desc, player_id asc
            """, countQuery = """
            with latest as (
                select q.player_id, row_number() over (partition by q.player_id, q.strategy_code order by q.calculated_at desc, q.id desc) as quote_order
                from player_quotes q where q.strategy_code = :strategy
            )
            select count(*) from latest q join players p on p.id = q.player_id
            where q.quote_order = 1
              and (cast(:league as varchar) is null or cast(p.league as varchar) = cast(:league as varchar))
              and (cast(:position as varchar) is null or exists (select 1 from player_positions pp
                   where pp.player_id = p.id and cast(pp.position as varchar) = cast(:position as varchar)))
            """, nativeQuery = true)
    Page<RankingRowProjection> findRanking(@Param("strategy") String strategy, @Param("league") String league,
                                           @Param("position") String position, Pageable pageable);
}

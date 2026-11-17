-- Reproduces PlayerQuoteRepository.findRanking and countQuery for the frontend request.
-- Parameters: strategy=OFFICIAL, league=NULL, position=NULL, page=0, size=20.
-- Run with psql -X -v ON_ERROR_STOP=1 -P pager=off -f ranking-explain.sql.
BEGIN READ ONLY;
\echo PAGE_QUERY
EXPLAIN (ANALYZE, BUFFERS)
WITH quote_history AS (
    SELECT q.player_id, q.strategy_code, q.strategy_version, q.quote_value,
           row_number() OVER (PARTITION BY q.player_id, q.strategy_code ORDER BY q.calculated_at DESC, q.id DESC) AS quote_order,
           lead(q.quote_value) OVER (PARTITION BY q.player_id, q.strategy_code ORDER BY q.calculated_at DESC, q.id DESC) AS previous_value
    FROM player_quotes q WHERE q.strategy_code = 'OFFICIAL'
), eligible AS (
    SELECT p.id AS player_id, p.full_name, p.team, p.league, q.quote_value AS current_value,
           q.previous_value, q.strategy_code, q.strategy_version
    FROM quote_history q JOIN players p ON p.id = q.player_id
    WHERE q.quote_order = 1
      AND (cast(NULL AS varchar) IS NULL OR cast(p.league AS varchar) = cast(NULL AS varchar))
      AND (cast(NULL AS varchar) IS NULL OR EXISTS (SELECT 1 FROM player_positions pp
           WHERE pp.player_id = p.id AND cast(pp.position AS varchar) = cast(NULL AS varchar)))
), ranked AS (
    SELECT row_number() OVER (ORDER BY current_value DESC, player_id ASC) AS ranking_position, eligible.* FROM eligible
)
SELECT ranking_position AS rank, player_id AS "playerId", full_name AS name, team, league,
       current_value AS "currentValue", previous_value AS "previousValue",
       strategy_code AS strategy, strategy_version AS version
FROM ranked ORDER BY current_value DESC, player_id ASC LIMIT 20 OFFSET 0;
\echo COUNT_QUERY
EXPLAIN (ANALYZE, BUFFERS)
WITH latest AS (
    SELECT q.player_id, row_number() OVER (PARTITION BY q.player_id, q.strategy_code ORDER BY q.calculated_at DESC, q.id DESC) AS quote_order
    FROM player_quotes q WHERE q.strategy_code = 'OFFICIAL'
)
SELECT count(*) FROM latest q JOIN players p ON p.id = q.player_id
WHERE q.quote_order = 1
  AND (cast(NULL AS varchar) IS NULL OR cast(p.league AS varchar) = cast(NULL AS varchar))
  AND (cast(NULL AS varchar) IS NULL OR EXISTS (SELECT 1 FROM player_positions pp
       WHERE pp.player_id = p.id AND cast(pp.position AS varchar) = cast(NULL AS varchar)));
ROLLBACK;

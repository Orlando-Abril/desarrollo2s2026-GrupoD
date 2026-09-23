-- Las bases creadas antes de Flyway (ddl-auto) tienen players.position NOT NULL de una versión
-- previa de la entidad. Las posiciones viven en player_positions, así que la columna legacy
-- rompe cada insert del catálogo. En bases creadas desde V1 no existe y esto es un no-op.
ALTER TABLE players DROP CONSTRAINT IF EXISTS players_position_check;
ALTER TABLE players DROP COLUMN IF EXISTS position;

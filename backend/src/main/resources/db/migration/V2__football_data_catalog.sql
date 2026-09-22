DO $$
BEGIN
    IF EXISTS (SELECT external_id FROM players WHERE external_id IS NOT NULL
               GROUP BY external_id HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Cannot enforce unique players.external_id: duplicate values exist';
    END IF;
END $$;

DROP INDEX IF EXISTS idx_players_external_id;
CREATE UNIQUE INDEX uq_players_external_id_not_null ON players(external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_players_league ON players(league);
CREATE INDEX idx_players_team_lower ON players(LOWER(team));
CREATE INDEX idx_player_positions_position ON player_positions(position, player_id);

CREATE TABLE player_token_allocations (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL UNIQUE REFERENCES players(id),
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    total_supply INTEGER NOT NULL CHECK (total_supply = 100),
    owner_quantity INTEGER NOT NULL CHECK (owner_quantity = 100),
    base_price NUMERIC(19,2) NOT NULL CHECK (base_price = 1.00),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE catalog_sync_audit_events (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    correlation_id UUID NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    action VARCHAR(64) NOT NULL,
    detail VARCHAR(1000) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(255),
    before_state TEXT,
    after_state TEXT,
    league VARCHAR(32),
    processed_count INTEGER,
    failure_code VARCHAR(64)
);
CREATE INDEX idx_catalog_audit_correlation ON catalog_sync_audit_events(correlation_id);
CREATE INDEX idx_catalog_audit_occurred_at ON catalog_sync_audit_events(occurred_at);

CREATE OR REPLACE FUNCTION reject_catalog_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'catalog_sync_audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER catalog_audit_no_update
BEFORE UPDATE OR DELETE ON catalog_sync_audit_events
FOR EACH ROW EXECUTE FUNCTION reject_catalog_audit_mutation();

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    balance NUMERIC(19,2),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE api_keys (
    id BIGSERIAL PRIMARY KEY,
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    key_prefix VARCHAR(255) NOT NULL,
    user_id BIGINT UNIQUE REFERENCES users(id),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP
);

CREATE TABLE players (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(255),
    full_name VARCHAR(255) NOT NULL,
    team VARCHAR(255) NOT NULL,
    league VARCHAR(32) NOT NULL,
    nationality VARCHAR(255),
    age INTEGER,
    height_cm INTEGER,
    market_value NUMERIC(19,2) NOT NULL
);

CREATE TABLE player_positions (
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    position VARCHAR(32) NOT NULL
);

CREATE INDEX idx_players_external_id ON players(external_id);


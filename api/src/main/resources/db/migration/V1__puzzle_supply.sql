CREATE TABLE puzzle_supply (
    puzzle_key varchar(64) NOT NULL,
    logical_hash varchar(64) NOT NULL,
    topology_key varchar(64) NOT NULL,
    proof_version text NOT NULL,
    player_version text NOT NULL,
    category text NOT NULL CHECK (category IN ('VERY_EASY','EASY','MEDIUM','CHALLENGING')),
    node_count integer NOT NULL CHECK (node_count BETWEEN 4 AND 53),
    edges text NOT NULL,
    givens text NOT NULL,
    points text NOT NULL,
    rules text NOT NULL,
    generator_version text NOT NULL,
    origin_seed bigint NOT NULL,
    origin_attempt integer NOT NULL,
    generation_spec text NOT NULL,
    source text NOT NULL CHECK (source IN ('BUNDLED','WORKER','LIVE')),
    created_at timestamptz NOT NULL DEFAULT now(),
    quarantine_reason text,
    PRIMARY KEY (puzzle_key, proof_version, player_version),
    UNIQUE (topology_key, category, proof_version, player_version),
    CHECK (node_count >= 14 OR category IN ('VERY_EASY','EASY'))
);
CREATE INDEX puzzle_supply_selection ON puzzle_supply (proof_version,player_version,category,node_count)
    WHERE quarantine_reason IS NULL;

CREATE TABLE supply_import (
    bank_hash varchar(64) NOT NULL,
    proof_version text NOT NULL,
    player_version text NOT NULL,
    completed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (bank_hash,proof_version,player_version)
);

CREATE TABLE discovery_control (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    paused boolean NOT NULL DEFAULT false
);
INSERT INTO discovery_control(singleton) VALUES (true);

CREATE TABLE discovery_cell (
    search_version text NOT NULL,
    category text NOT NULL,
    size text NOT NULL,
    minimum integer NOT NULL,
    maximum integer NOT NULL,
    next_seed bigint NOT NULL,
    attempts bigint NOT NULL DEFAULT 0,
    added bigint NOT NULL DEFAULT 0,
    duplicates bigint NOT NULL DEFAULT 0,
    failures bigint NOT NULL DEFAULT 0,
    lease_owner uuid,
    lease_until timestamptz,
    last_completed_at timestamptz,
    last_outcome text,
    last_duration_ms bigint,
    last_stats text,
    PRIMARY KEY (search_version,category,size),
    CHECK (minimum >= 4 AND maximum <= 53 AND minimum <= maximum)
);

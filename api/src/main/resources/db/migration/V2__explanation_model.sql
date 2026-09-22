ALTER TABLE puzzle_supply ADD COLUMN explanation_model varchar(32) NOT NULL DEFAULT 'PLAYER_V1';
ALTER TABLE puzzle_supply ADD CONSTRAINT puzzle_supply_explanation_model CHECK (explanation_model = 'PLAYER_V1');

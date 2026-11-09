CREATE TABLE events (
    event_id    CHAR(36)    NOT NULL,
    game_id     VARCHAR(64) NOT NULL,
    seq         INT         NOT NULL,
    type        VARCHAR(32) NOT NULL,
    payload     JSON        NOT NULL,
    received_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (event_id),
    KEY idx_events_game_seq (game_id, seq)
);

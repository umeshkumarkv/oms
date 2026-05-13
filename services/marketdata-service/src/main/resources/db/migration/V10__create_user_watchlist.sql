-- SCRUM-117: Per-user watchlist persistence
-- Stores which market symbols each trader has added to their watchlist.
-- user_id comes from the JWT (forwarded as X-User-Id by the API gateway).

CREATE TABLE IF NOT EXISTS market_schema.user_watchlist
(
    id       UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id  UUID        NOT NULL,
    symbol   VARCHAR(20) NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_watchlist PRIMARY KEY (id),
    CONSTRAINT uq_user_watchlist_user_symbol UNIQUE (user_id, symbol)
);

CREATE INDEX IF NOT EXISTS idx_user_watchlist_user_id
    ON market_schema.user_watchlist (user_id);

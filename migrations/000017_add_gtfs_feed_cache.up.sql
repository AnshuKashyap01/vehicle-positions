-- The last GTFS static feed that downloaded and indexed cleanly, so the server
-- can start on it when the agency's GTFS host is down. One row: only the
-- configured feed is ever cached.
CREATE TABLE IF NOT EXISTS gtfs_feed_cache (
    id         SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    source     TEXT        NOT NULL,
    body       BYTEA       NOT NULL,
    sha256     BYTEA       NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE discovered_skipped (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    osm_id        VARCHAR(128) NOT NULL UNIQUE,
    name          VARCHAR(256) NOT NULL,
    skip_reason   VARCHAR(32)  NOT NULL,
    phone         VARCHAR(64),
    address       TEXT,
    industry      VARCHAR(64),
    country_code  CHAR(2),
    source        VARCHAR(32)  NOT NULL DEFAULT 'osm',
    discovered_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_skipped_osm_id ON discovered_skipped(osm_id);
CREATE INDEX idx_skipped_reason ON discovered_skipped(skip_reason);

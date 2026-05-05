CREATE TABLE discovery_filters (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(128) NOT NULL,
    industry     VARCHAR(64),
    country_code CHAR(2),
    city         VARCHAR(128),
    keywords     TEXT[],
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_discovery_filters_active ON discovery_filters(is_active);

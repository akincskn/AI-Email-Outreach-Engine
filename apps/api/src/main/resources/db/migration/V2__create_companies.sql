CREATE TABLE companies (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    domain           VARCHAR(255) NOT NULL UNIQUE,
    name             VARCHAR(255) NOT NULL,
    website_url      VARCHAR(512),

    source           VARCHAR(64)  NOT NULL,
    source_metadata  JSONB,
    discovered_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    country_code     CHAR(2),
    city             VARCHAR(128),

    analysis         JSONB,
    analysis_at      TIMESTAMPTZ,
    analysis_version VARCHAR(32),

    status           VARCHAR(32)  NOT NULL DEFAULT 'NEW',
    status_reason    TEXT,

    embedding        vector(384),

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    archived_at      TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ
);

CREATE INDEX idx_companies_status    ON companies(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_companies_domain    ON companies(domain) WHERE deleted_at IS NULL;
CREATE INDEX idx_companies_country   ON companies(country_code);
CREATE INDEX idx_companies_analysis  ON companies USING gin (analysis);
CREATE INDEX idx_companies_embedding ON companies USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

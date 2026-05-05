CREATE TABLE volume_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sent_date   DATE        NOT NULL UNIQUE,
    sent_count  INTEGER     NOT NULL DEFAULT 0,
    daily_cap   INTEGER     NOT NULL,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_volume_log_date ON volume_log(sent_date DESC);

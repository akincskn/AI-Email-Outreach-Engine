CREATE TABLE pipeline_runs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_name    VARCHAR(64)  NOT NULL,
    n8n_execution_id VARCHAR(64),

    started_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMPTZ,
    duration_ms      INTEGER,

    status           VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    items_processed  INTEGER DEFAULT 0,
    items_succeeded  INTEGER DEFAULT 0,
    items_failed     INTEGER DEFAULT 0,

    error_summary    TEXT,
    metadata         JSONB
);

CREATE INDEX idx_pipeline_runs_workflow   ON pipeline_runs(workflow_name);
CREATE INDEX idx_pipeline_runs_started_at ON pipeline_runs(started_at DESC);

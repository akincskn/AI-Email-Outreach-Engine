-- Görev 10.2 — async "Run All" jobs. The synchronous /run-all endpoint took
-- 5-13 minutes and tripped Next.js's 60s server-action fetch timeout. Akın now
-- POSTs run-all-async (returns a jobId immediately) and the dashboard polls this
-- table for status + per-filter progress while a background @Async thread runs.
CREATE TABLE pipeline_jobs (
    id               UUID PRIMARY KEY,
    job_type         VARCHAR(32)  NOT NULL,   -- RUN_ALL, RUN_FILTER
    status           VARCHAR(32)  NOT NULL,   -- PENDING, RUNNING, COMPLETED, FAILED
    started_at       TIMESTAMPTZ  NOT NULL,
    completed_at     TIMESTAMPTZ,
    progress_message TEXT,                     -- "Pipeline 3/5: TR Marketing Agencies"
    result_json      JSONB,                    -- RunAllResult on completion
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pipeline_jobs_status     ON pipeline_jobs(status);
CREATE INDEX idx_pipeline_jobs_created_at ON pipeline_jobs(created_at DESC);

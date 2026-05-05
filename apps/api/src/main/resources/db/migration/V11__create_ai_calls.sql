CREATE TABLE ai_calls (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    company_id       UUID REFERENCES companies(id) ON DELETE SET NULL,
    agent_name       VARCHAR(32)  NOT NULL,

    provider         VARCHAR(32)  NOT NULL,
    model            VARCHAR(64)  NOT NULL,
    prompt_version   VARCHAR(32),

    input_tokens     INTEGER,
    output_tokens    INTEGER,
    total_tokens     INTEGER GENERATED ALWAYS AS
                         (COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)) STORED,
    duration_ms      INTEGER,

    success          BOOLEAN      NOT NULL,
    error_message    TEXT,

    prompt_snippet   VARCHAR(512),
    response_snippet VARCHAR(512),

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_calls_company    ON ai_calls(company_id);
CREATE INDEX idx_ai_calls_agent      ON ai_calls(agent_name);
CREATE INDEX idx_ai_calls_created_at ON ai_calls(created_at DESC);
CREATE INDEX idx_ai_calls_failed     ON ai_calls(success) WHERE success = FALSE;

CREATE TABLE suppression_list (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL UNIQUE,

    reason         VARCHAR(64)  NOT NULL,
    source_send_id UUID REFERENCES email_sends(id) ON DELETE SET NULL,
    notes          TEXT,

    suppressed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ
);

CREATE INDEX idx_suppression_email  ON suppression_list(email);
CREATE INDEX idx_suppression_active ON suppression_list(email)
    WHERE expires_at IS NULL OR expires_at > NOW();

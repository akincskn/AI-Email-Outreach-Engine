CREATE TABLE email_bounces (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    send_id      UUID        NOT NULL REFERENCES email_sends(id) ON DELETE CASCADE,

    bounce_type  VARCHAR(32) NOT NULL,
    bounce_reason TEXT,
    bounce_code  VARCHAR(16),
    raw_message  TEXT,

    detected_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_bounces_send ON email_bounces(send_id);
CREATE INDEX idx_email_bounces_type ON email_bounces(bounce_type);

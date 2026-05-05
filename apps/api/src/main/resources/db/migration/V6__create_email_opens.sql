CREATE TABLE email_opens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    send_id         UUID        NOT NULL REFERENCES email_sends(id) ON DELETE CASCADE,

    opened_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_agent      VARCHAR(512),
    ip_country_code CHAR(2)
);

CREATE INDEX idx_email_opens_send      ON email_opens(send_id);
CREATE INDEX idx_email_opens_opened_at ON email_opens(opened_at DESC);

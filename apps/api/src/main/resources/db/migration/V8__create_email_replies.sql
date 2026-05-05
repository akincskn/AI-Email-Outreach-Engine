CREATE TABLE email_replies (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    send_id        UUID REFERENCES email_sends(id) ON DELETE SET NULL,

    from_email     VARCHAR(255) NOT NULL,
    subject        VARCHAR(255),
    body_text      TEXT,
    in_reply_to    VARCHAR(255),

    classification VARCHAR(32),
    is_handled     BOOLEAN      NOT NULL DEFAULT FALSE,

    received_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_replies_send      ON email_replies(send_id);
CREATE INDEX idx_email_replies_unhandled ON email_replies(received_at DESC) WHERE is_handled = FALSE;

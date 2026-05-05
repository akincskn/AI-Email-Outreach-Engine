CREATE TABLE email_sends (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id              UUID         NOT NULL REFERENCES email_drafts(id) ON DELETE RESTRICT,
    company_id            UUID         NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,

    to_email              VARCHAR(255) NOT NULL,
    from_email            VARCHAR(255) NOT NULL,
    subject               VARCHAR(255) NOT NULL,
    message_id            VARCHAR(255) UNIQUE,

    status                VARCHAR(32)  NOT NULL DEFAULT 'QUEUED',
    smtp_response         TEXT,
    error_message         TEXT,
    retry_count           INTEGER      NOT NULL DEFAULT 0,

    tracking_pixel_token  VARCHAR(64),
    unsubscribe_token     VARCHAR(64),

    queued_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at               TIMESTAMPTZ,
    failed_at             TIMESTAMPTZ
);

CREATE INDEX idx_email_sends_status      ON email_sends(status);
CREATE INDEX idx_email_sends_to_email    ON email_sends(to_email);
CREATE INDEX idx_email_sends_message_id  ON email_sends(message_id);
CREATE INDEX idx_email_sends_sent_at     ON email_sends(sent_at DESC);
CREATE INDEX idx_email_sends_unsub_token ON email_sends(unsubscribe_token);
CREATE INDEX idx_email_sends_pixel_token ON email_sends(tracking_pixel_token);

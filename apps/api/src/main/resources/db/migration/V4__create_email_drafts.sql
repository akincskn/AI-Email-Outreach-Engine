CREATE TABLE email_drafts (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id             UUID         NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    email_account_id       UUID         NOT NULL REFERENCES email_accounts(id) ON DELETE CASCADE,

    subject                VARCHAR(255) NOT NULL,
    body_html              TEXT         NOT NULL,
    body_text              TEXT         NOT NULL,
    language               CHAR(2)      NOT NULL,

    model_used             VARCHAR(64),
    prompt_version         VARCHAR(32),
    personalization_signals JSONB,
    warnings               JSONB,

    status                 VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    edited_subject         VARCHAR(255),
    edited_body_html       TEXT,
    edited_body_text       TEXT,
    rejection_reason       TEXT,

    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    approved_at            TIMESTAMPTZ,
    rejected_at            TIMESTAMPTZ
);

CREATE INDEX idx_email_drafts_company ON email_drafts(company_id);
CREATE INDEX idx_email_drafts_status  ON email_drafts(status);
CREATE INDEX idx_email_drafts_pending ON email_drafts(created_at DESC) WHERE status = 'PENDING';

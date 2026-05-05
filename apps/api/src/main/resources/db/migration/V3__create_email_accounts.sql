CREATE TABLE email_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID        NOT NULL REFERENCES companies(id) ON DELETE CASCADE,

    email           VARCHAR(255) NOT NULL,
    prefix_type     VARCHAR(32)  NOT NULL,
    extracted_from  VARCHAR(512),

    is_valid_format BOOLEAN      NOT NULL DEFAULT TRUE,
    is_generic      BOOLEAN      NOT NULL DEFAULT TRUE,
    validation_note TEXT,

    extracted_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT email_accounts_unique UNIQUE (company_id, email)
);

CREATE INDEX idx_email_accounts_company ON email_accounts(company_id);
CREATE INDEX idx_email_accounts_email   ON email_accounts(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_email_accounts_prefix  ON email_accounts(prefix_type);

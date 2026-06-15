-- EmailAccount ve EmailSend, BaseEntity'den (created_at + updated_at, @CreatedDate/
-- @LastModifiedDate ile) türüyor; ancak V3 ve V5 migration'larında bu audit kolonları
-- eksik kalmış. Hibernate ddl-auto=validate eksik kolonlarda başlangıçta patlıyor.
-- DEFAULT NOW() mevcut satırları (varsa) güvenle dolduruyor.

ALTER TABLE email_accounts ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE email_sends    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE email_sends    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

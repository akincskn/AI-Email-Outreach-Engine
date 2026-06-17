ALTER TABLE email_drafts
    ADD COLUMN matched_product_slug   VARCHAR(64),
    ADD COLUMN secondary_product_slug VARCHAR(64),
    ADD COLUMN match_confidence       NUMERIC(3, 2),
    ADD COLUMN match_reasoning        TEXT;

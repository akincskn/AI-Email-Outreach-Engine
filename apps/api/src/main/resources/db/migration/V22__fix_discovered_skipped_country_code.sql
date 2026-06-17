-- V21 created discovered_skipped.country_code as CHAR(2) (bpchar), but the
-- DiscoveredSkipped entity maps it as @Column(length = 2) -> VARCHAR(2), so
-- Hibernate ddl-auto=validate blows up at startup (same class of bug V15 fixed
-- for the older tables). CHAR(2) also right-pads, which is unwanted for country
-- codes. Normalize to VARCHAR(2).
--
-- V21 is already applied, so we cannot edit it (checksum) — fix forward here.
ALTER TABLE discovered_skipped ALTER COLUMN country_code TYPE VARCHAR(2);

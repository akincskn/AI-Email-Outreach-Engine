-- Görev 12 — daily quota + multi-city discovery.
--
-- daily_quota: max new drafts a filter may produce per day (Gmail-safe). The
-- pipeline orchestrator counts today's drafts per filter and stops once the
-- quota is reached, so re-running "Run All" the same day cannot overshoot.
--
-- cities: a filter can now search several cities in one run (Apify scale). The
-- legacy single `city` column is KEPT for backward compatibility (OSM still
-- keys off it); it is deprecated and will be dropped in a later migration.
ALTER TABLE discovery_filters
    ADD COLUMN daily_quota INT NOT NULL DEFAULT 4,
    ADD COLUMN cities      TEXT[] DEFAULT NULL;

-- Seed cities[] from the existing single city so current filters keep working.
UPDATE discovery_filters
SET cities = ARRAY[city]
WHERE city IS NOT NULL AND cities IS NULL;

-- Quota tracking needs to attribute each draft to the filter that produced it.
-- Companies discovered before this migration have no filter recorded (NULL).
ALTER TABLE companies
    ADD COLUMN discovery_filter_id UUID REFERENCES discovery_filters(id);

CREATE INDEX idx_companies_discovery_filter ON companies(discovery_filter_id);

-- Görev 11 — second discovery provider (Apify Google Maps scraper).
-- Each filter now records which provider it discovers from. Defaults to 'osm'
-- so every existing filter keeps its current (free) behaviour.
ALTER TABLE discovery_filters
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'osm';

-- Switch TR Property Management to Apify: OSM returns too few results in Turkey
-- (only ~2 places), while Apify Google Maps yields ~20 per search. Targeted by
-- name because the V19 seed assigns ids via gen_random_uuid() (no stable id).
-- The other 6 filters stay on OSM to preserve the Apify free-tier credits.
UPDATE discovery_filters
SET source = 'apify'
WHERE name = 'TR Property Management';

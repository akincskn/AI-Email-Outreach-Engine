-- Görev 4 — Sectoral discovery filters.
-- Each filter biases the Matcher toward one product (target_product). The column
-- is NULLABLE so any legacy generic filter without a product stays valid.
ALTER TABLE discovery_filters
    ADD COLUMN target_product VARCHAR(64);

-- Out with the old generic filters (V14 seed). They targeted no product and are
-- no longer useful now that discovery is sector → product driven.
DELETE FROM discovery_filters;

-- The 7 sector filters — one per product. Faz 1 launches with TR Property
-- Management only; the rest are seeded inactive so they can be flipped on later
-- (after Gmail warming) without another migration.
INSERT INTO discovery_filters (name, industry, country_code, city, keywords, target_product, is_active) VALUES
('TR Property Management',   'property_management', 'TR', 'İstanbul', ARRAY['apartman yönetimi', 'site yönetimi', 'aidat'],   'kolayaidat',           TRUE),
('TR Small Business Sites',  'small_business',      'TR', NULL,       ARRAY['kurumsal site', 'kobi', 'web sitesi'],            'cerezmatik',           FALSE),
('TR Restaurants & Cafes',   'restaurant',          'TR', 'İstanbul', ARRAY['restoran', 'cafe', 'menü'],                       'ai-chatbot-platform',  FALSE),
('TR SMB Forms',             'small_business',      'TR', NULL,       ARRAY['başvuru formu', 'iletişim formu'],                'formjet',              FALSE),
('US SaaS Competitor Watch', 'saas',                'US', NULL,       ARRAY['startup', 'b2b', 'competitor'],                   'rivalradar',           FALSE),
('US Local Visibility',      'local_business',      'US', NULL,       ARRAY['local seo', 'ai search', 'visibility'],           'geo-analyzer',         FALSE),
('UK Marketing Agencies',    'marketing',           'GB', 'London',   ARRAY['agency', 'digital', 'outreach'],                  'leadpilot',            FALSE);

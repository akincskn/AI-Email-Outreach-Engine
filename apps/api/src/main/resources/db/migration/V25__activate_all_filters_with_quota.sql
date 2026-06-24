-- Görev 12 — activate the TR outreach filters with a daily quota, multi-keyword
-- and multi-city search. US filters stay inactive (Akın's strategy is TR-first).
--
-- NOTE: `keywords` and `cities` are PostgreSQL TEXT[] columns (see V13/V24), so
-- they use ARRAY[...] literals — NOT jsonb.

-- KolayAidat — apartment/building management.
UPDATE discovery_filters SET
    is_active   = true,
    daily_quota = 4,
    cities      = ARRAY['İstanbul', 'Ankara', 'İzmir', 'Bursa', 'Antalya', 'Kocaeli'],
    keywords    = ARRAY['apartman yönetimi', 'site yönetimi', 'bina yönetimi', 'aidat']
WHERE name = 'TR Property Management';

-- AI Chatbot Platform — restaurants & cafes.
UPDATE discovery_filters SET
    is_active   = true,
    daily_quota = 4,
    cities      = ARRAY['İstanbul', 'Ankara', 'İzmir', 'Antalya'],
    keywords    = ARRAY['restoran', 'restaurant', 'cafe', 'kafe']
WHERE name = 'TR Restaurants & Cafes';

-- LeadPilot — the V19 seed called this "UK Marketing Agencies"; Akın's focus is
-- TR, so re-point it to Turkey (rename + country_code) before activating it.
UPDATE discovery_filters SET
    name         = 'TR Marketing Agencies',
    country_code = 'TR',
    is_active    = true,
    daily_quota  = 4,
    cities       = ARRAY['İstanbul', 'Ankara'],
    keywords     = ARRAY['dijital ajans', 'marketing agency', 'reklam ajansı', 'seo ajansı']
WHERE name = 'UK Marketing Agencies';

-- Çerezmatik — small business sites.
UPDATE discovery_filters SET
    is_active   = true,
    daily_quota = 4,
    cities      = ARRAY['İstanbul', 'Ankara', 'İzmir'],
    keywords    = ARRAY['e-ticaret', 'online satış', 'shopify', 'woocommerce']
WHERE name = 'TR Small Business Sites';

-- FormJet — SMB forms.
UPDATE discovery_filters SET
    is_active   = true,
    daily_quota = 4,
    cities      = ARRAY['İstanbul', 'Ankara'],
    keywords    = ARRAY['form sistemi', 'anket', 'başvuru formu']
WHERE name = 'TR SMB Forms';

-- US filters stay inactive for the TR-first launch.
UPDATE discovery_filters SET is_active = false
WHERE name IN ('US SaaS Competitor Watch', 'US Local Visibility');

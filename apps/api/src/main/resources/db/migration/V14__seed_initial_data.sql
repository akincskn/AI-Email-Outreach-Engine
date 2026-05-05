INSERT INTO discovery_filters (name, industry, country_code, city, keywords) VALUES
('Istanbul Restaurants',      'restaurant', 'TR', 'Istanbul',  ARRAY['cafe', 'lokanta']),
('Ankara Tech Companies',     'saas',       'TR', 'Ankara',    ARRAY['startup', 'yazilim']),
('Istanbul Marketing',        'marketing',  'TR', 'Istanbul',  ARRAY['dijital', 'reklam']),
('NYC SaaS Startups',         'saas',       'US', 'New York',  ARRAY['startup', 'b2b']),
('London Marketing Agencies', 'marketing',  'GB', 'London',    ARRAY['agency', 'digital']),
('Amsterdam Tech',            'saas',       'NL', 'Amsterdam', ARRAY['startup', 'scale-up']);

INSERT INTO volume_log (sent_date, sent_count, daily_cap)
VALUES (CURRENT_DATE, 0, 0);

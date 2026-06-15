-- JPA entity'leri bu kolonları varchar(2) olarak map'liyor (@Column(length = 2)),
-- fakat V2/V4/V6/V13 migration'ları CHAR(2) (bpchar) kullanmıştı.
-- Hibernate ddl-auto=validate bu tip uyuşmazlığında başlangıçta patlıyor.
-- CHAR(2) ayrıca sağ-boşluk padding yaptığından ülke/dil kodları için
-- istenmeyen bir davranış; varchar(2)'ye normalize ediyoruz.

ALTER TABLE companies         ALTER COLUMN country_code    TYPE VARCHAR(2);
ALTER TABLE discovery_filters ALTER COLUMN country_code    TYPE VARCHAR(2);
ALTER TABLE email_drafts      ALTER COLUMN language        TYPE VARCHAR(2);
ALTER TABLE email_opens       ALTER COLUMN ip_country_code TYPE VARCHAR(2);

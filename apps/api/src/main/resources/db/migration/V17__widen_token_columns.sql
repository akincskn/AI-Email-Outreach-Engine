-- HmacTokenService, token'ı hex(payload|nonce|sig) olarak üretiyor:
-- email payload'lı unsubscribe token'ı ~196 karakter, pixel token'ı ~158.
-- V5 bu kolonları VARCHAR(64) yapmıştı → INSERT sırasında "value too long".
-- (Hibernate ddl-auto=validate kolon uzunluğunu kontrol etmediği için startup'ta
-- yakalanmadı, runtime'da patladı.) Uzun kurumsal adresler için 512 güvenli pay.

ALTER TABLE email_sends ALTER COLUMN tracking_pixel_token TYPE VARCHAR(512);
ALTER TABLE email_sends ALTER COLUMN unsubscribe_token    TYPE VARCHAR(512);

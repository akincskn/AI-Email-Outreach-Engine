# Production Readiness Checklist

> **Akın için 18 Mayıs öncesi/sonrası yapılacaklar.**
> Her satır tamamlanınca işaretle.

---

## Kod Durumu (Claude Code tamamladı)

- [x] Tüm Faz 1 feature'ları implement edildi (Task 1–23)
- [x] Rate limiting — Bucket4j, 100 req/min/IP (Task 24)
- [x] Dockerfile multi-stage + HEALTHCHECK + non-root (Task 25)
- [x] render.yaml + .dockerignore
- [x] BUG FIX: Pipeline kopukluğu — company-discovery.json write adımı eklendi
- [x] BUG FIX: Unsubscribe PLACEHOLDER — EmailSendOrchestrator'da gerçek token inject
- [x] BUG FIX: ReplyTracker SEEN flag eksikti — düzeltildi
- [x] MockLlmClient — local profile'da gerçek API çağrısı yok (app.ai.mock-enabled)
- [x] application.yml prod profile — logging, SQL log, actuator kısıtlandı
- [x] .env.example tam (AUTH_SECRET, DASHBOARD_USERNAME, SPRING_API_URL eklendi)
- [x] 40 unit + integration test — tümü geçiyor

---

## 18 Mayıs Öncesi — Akın'ın Hazırlıkları

- [ ] **Gmail hesabı 14 gün doldu** (akin.tools.outreach@gmail.com, 2026-05-04 açıldı)
  - Normal kullanım: profil tamamla, 3-5 mail al/gönder
  - 2FA aktif mi?
  - App Password alındı mı?

- [ ] **Groq API key al:** https://console.groq.com → Keys → Create key

- [ ] **Gemini API key al:** https://aistudio.google.com → Get API key

- [ ] **Google Maps API key al:**
  - GCP Console → Maps Platform → Places API enable
  - API key oluştur, HTTP referrer kısıtla (opsiyonel)

- [ ] **Slack webhook:**
  - `#email-outreach` channel oluştur
  - https://api.slack.com/apps → Create App → Incoming Webhooks → Activate
  - Webhook URL'i kopyala

- [ ] **Fiziksel adres hazırla** (CAN-SPAM zorunlu):
  - Ev adresi veya P.O. Box
  - Bu adres her emailin footer'ında görünür

---

## 18 Mayıs Sonrası — Deployment

- [ ] **Neon PostgreSQL:** proje oluştur, extensions aktive et, connection string al
  → docs/DEPLOYMENT.md § 1

- [ ] **Render API:** service oluştur, env var'ları gir, deploy et
  → docs/DEPLOYMENT.md § 2

- [ ] **Render N8N:** service + disk oluştur, env var'ları gir
  → docs/DEPLOYMENT.md § 3

- [ ] **N8N workflow import:** `workflows/company-discovery.json`
  → Workflow'u Active yap, SPRING_API_URL set et

- [ ] **Vercel:** proje oluştur, env var'ları gir, deploy et
  → docs/DEPLOYMENT.md § 4

- [ ] **CORS:** Vercel URL'i Render'ın `CORS_ALLOWED_ORIGINS`'ine ekle

---

## Smoke Test (Deploy Sonrası)

- [ ] docs/SMOKE_TEST.md'yi baştan sona çalıştır
- [ ] **Geçer kriterler:**
  - [ ] `/actuator/health` → UP
  - [ ] Discovery çalışıyor → companies tablosunda kayıt
  - [ ] Drafts oluşuyor → /drafts sayfasında görünüyor
  - [ ] Body'de PLACEHOLDER YOK
  - [ ] mail-tester.com ≥8/10
  - [ ] Tracking pixel → email_opens kaydı
  - [ ] Unsubscribe → suppression_list kaydı
  - [ ] Suppression koruması çalışıyor
  - [ ] Reply tracking (IMAP)
  - [ ] Bounce tracking (IMAP)
  - [ ] Volume limiter cap'i koruyor

---

## İlk Hafta (Task 27 — CHECKLIST.md)

- [ ] **Gün 1-3:** Sadece kendi alternatif email'lerine gönder (5/gün cap)
  - Spam'a düşüyor mu?
  - Open/reply tracking çalışıyor mu?

- [ ] **Gün 4-7:** İlk gerçek prospect
  - Akın elle seçer (ürünlerine en uygun şirket)
  - Her maili onayla ve düzenle, AI'a tam güvenme henüz

- [ ] Bounce rate izle — >%2 ise dur
- [ ] Reply gelirse Akın manuel yanıtlar

---

## Sistem Tamamlandı

**Kod tamamlanma tarihi:** 6 Mayıs 2026  
**Warming bitiş:** 18 Mayıs 2026  
**İlk gerçek gönderim hedefi:** 18-20 Mayıs 2026

# CHECKLIST.md — AI Email Outreach Engine | Faz 1

> Bu dosya **sıralı task listesi**. Claude Code (veya Akın) yukarıdan aşağı takip eder.
> Her task tamamlandığında `[ ]` → `[x]` işaretlenir.
> Bir task tamamlanmadan sonrakine geçilmez (dependency var).

## Faz 1 Hedef Tanımı

**Faz 1 başarı kriteri:**
- Şirketler otomatik olarak keşfediliyor
- AI Analyzer şirketleri analiz ediyor
- AI Writer kişiselleştirilmiş email yazıyor
- Akın dashboard'da onaylayabiliyor
- SMTP üzerinden email gönderiliyor
- Bounce + reply tracking çalışıyor
- 1 hafta operasyonel test, en az 30 onaylanmış email gönderilmiş

**Faz 1 dışında olanlar (Faz 2+):**
- Custom domain + Resend
- Otomatik gönderim (auto-approve)
- Reply auto-classifier
- A/B testing
- Reddit/social listener (back-burner)
- Multi-account rotation

---

## 0. Hazırlık (Akın yapacak, Claude Code'dan önce)

- [ ] **Yeni Gmail hesabı oluştur:** `akin.tools.outreach@gmail.com` (veya benzer)
  - 2FA aktive et
  - App Password oluştur (Settings → Security → App passwords)
  - **2 hafta boyunca normal kullan** (mail al, gönder, profil tamamla)
- [ ] **Groq API key:** https://console.groq.com → API key
- [ ] **Google AI Studio (Gemini):** https://aistudio.google.com → API key
- [ ] **Google Maps API key:** Cloud Console → Maps Platform → API key (Faz 1 için Places API yeterli)
- [ ] **Slack workspace:**
  - Channel: `#email-outreach`
  - Incoming webhook URL al
- [ ] **Neon Postgres:**
  - Yeni project: `ai-email-outreach`
  - pgvector + pgcrypto extension'ları aktive et
  - Connection string al
- [ ] **GitHub repo:** `ai-email-outreach` (private başla)
- [ ] **Fiziksel adres:** CAN-SPAM için gerçek bir iş adresi (P.O. Box veya home address — public OK)
- [ ] **Hesap warming başlat:** Gmail hesabını 2 hafta normal kullan

---

## 1. Repo İnit

- [ ] Monorepo iskeleti:
  ```
  ai-email-outreach/
  ├── apps/
  │   ├── api/          # Spring Boot
  │   └── web/          # Next.js
  ├── workflows/        # N8N JSON exports
  ├── docs/             # 5 markdown
  ├── docker-compose.yml
  ├── .gitignore
  ├── .env.example
  └── README.md
  ```
- [ ] `docs/` klasörünü repo'ya kopyala
- [ ] `.gitignore` standart (Java, Node, IDE, .env)
- [ ] `.env.example` — tüm env vars boş değerlerle
- [ ] `README.md` — kısa: ne, nasıl run, docs'a yönlendirme
- [ ] İlk commit: `chore: initial repo structure`

---

## 2. Local Dev Ortamı

- [ ] `docker-compose.yml`:
  - Postgres 16 + pgvector image (`pgvector/pgvector:pg16`)
  - N8N image (latest stable)
  - Volumes (persistent data)
  - **MailHog veya Mailpit** (local SMTP test için, Gmail'i test ederken yakmamak için)
- [ ] `docker compose up`, hepsi çalışıyor mu test
- [ ] N8N admin user oluştur, login yap
- [ ] DBeaver/pgAdmin ile DB'ye bağlan, pgvector + pgcrypto extension'ları aktif mi kontrol

---

## 3. Spring Boot Backend — İskelet

- [ ] `apps/api/pom.xml`:
  - Spring Boot 3.x parent
  - Java 21 target
  - Dependencies: web, data-jpa, validation, security, actuator, devtools, mail
  - Flyway, postgresql driver
  - MapStruct, Lombok
  - Resilience4j
  - Bucket4j (rate limit)
  - WebClient (reactive HTTP)
  - Jsoup (HTML parsing)
  - Testcontainers
- [ ] Package structure:
  ```
  com.akincoskun.outreach/
  ├── OutreachApplication.java
  ├── config/
  ├── controller/
  ├── service/
  ├── service/agent/      # AnalyzerService, WriterService
  ├── service/email/      # SmtpService, BounceService, ReplyService, SuppressionService
  ├── service/discovery/  # CompanyDiscoveryService, EmailExtractionService
  ├── repository/
  ├── domain/
  ├── dto/
  ├── integration/        # GroqClient, GeminiClient, GoogleMapsClient
  └── exception/
  ```
- [ ] `application.yml` (profiles: local, prod)
- [ ] Boot edip `/actuator/health` 200 döndüğünü gör

---

## 4. Database — Migrations

- [ ] Flyway config
- [ ] `V1__enable_extensions.sql`
- [ ] `V2__create_companies.sql`
- [ ] `V3__create_email_accounts.sql`
- [ ] `V4__create_email_drafts.sql`
- [ ] `V5__create_email_sends.sql`
- [ ] `V6__create_email_opens.sql`
- [ ] `V7__create_email_bounces.sql`
- [ ] `V8__create_email_replies.sql`
- [ ] `V9__create_suppression_list.sql`
- [ ] `V10__create_volume_log.sql`
- [ ] `V11__create_ai_calls.sql`
- [ ] `V12__create_pipeline_runs.sql`
- [ ] `V13__create_discovery_filters.sql`
- [ ] `V14__seed_initial_data.sql` — başlangıç filtreleri
- [ ] Boot et, migrations geçti mi
- [ ] DB'de tablolar var mı

---

## 5. Domain Entities + Repositories

- [ ] `Company` entity + repository
  - JsonType converter (analysis JSONB için)
- [ ] `EmailAccount` entity + repository
- [ ] `EmailDraft` entity + repository
- [ ] `EmailSend` entity + repository
- [ ] `EmailOpen` entity + repository
- [ ] `EmailBounce` entity + repository
- [ ] `EmailReply` entity + repository
- [ ] `SuppressionListEntry` entity + repository
- [ ] `VolumeLog` entity + repository
- [ ] `AiCall` entity + repository
- [ ] `PipelineRun` entity + repository
- [ ] `DiscoveryFilter` entity + repository
- [ ] Vector type için Hibernate custom type (pgvector)
- [ ] Audit fields: `@CreatedDate`, `@LastModifiedDate`
- [ ] Soft delete: `@SQLRestriction("deleted_at IS NULL")`
- [ ] Tüm entity'ler için unit test

---

## 6. DTO + MapStruct

- [ ] DTO'lar:
  - `CompanyResponse`, `CompanyDiscoverRequest`
  - `EmailAccountResponse`
  - `EmailDraftResponse`, `EmailDraftApproveRequest`
  - `EmailSendResponse`
  - `SuppressionEntryRequest`
  - `DiscoveryFilterRequest/Response`
- [ ] MapStruct mapper'lar
- [ ] Jakarta Validation: `@NotNull`, `@Size`, `@Email`, vs.

---

## 7. AI Integration Layer

- [ ] `LlmClient` interface
- [ ] `GroqClient` (WebClient + Llama 3.3 70B)
  - Token tracking → `ai_calls` tablosu
- [ ] `GeminiClient` (fallback)
- [ ] `LlmRouter` — primary/fallback logic
  - Resilience4j circuit breaker
- [ ] Unit test (mock WebClient)
- [ ] Integration test (real Groq smoke test)

---

## 8. Discovery Layer

- [ ] `GoogleMapsClient` — Places API entegrasyonu
  - Search by industry + city
  - Domain extraction
  - Rate limit awareness
- [ ] `CompanyDiscoveryService`
  - DiscoveryFilter'a göre Google Maps query
  - Duplicate prevention (domain unique)
  - `companies` upsert
- [ ] Manual CSV upload endpoint (`POST /api/v1/companies/import-csv`)
- [ ] Unit + integration test

---

## 9. Email Extraction

- [ ] `EmailExtractionService`
  - Şirket sitesini fetch (timeout 10s)
  - Cheerio/Jsoup ile parse
  - `/contact`, `/iletisim`, ana sayfa
  - Email regex
  - **Whitelist filter** — jenerik prefix'ler
  - **Blacklist filter** — kişi-spesifik pattern'ler
  - `email_accounts` insert
- [ ] Unit test (mock HTML, expected emails)
- [ ] Edge case: `mailto:` link'leri, escaped emails, JS-rendered

---

## 10. Analyzer Agent

- [ ] `AnalyzerService`
  - Site içeriği fetch (homepage + contact)
  - HTML clean (Jsoup) → text 5000 chars
  - LLM call (analyzer_v1 prompt)
  - JSON schema parse
  - `companies.analysis` JSONB write
  - `is_target_country` false → status `BLACKLISTED`
  - `skip_reason` not null → status `BLACKLISTED`
- [ ] Unit test (mock LLM)
- [ ] Integration test (gold set: 5 örnek)

---

## 11. Writer Agent

- [ ] `WriterService`
  - Company analysis + EmailAccount + Akın profile + 7 product list
  - LLM call (writer_v1 prompt)
  - JSON schema parse
  - **Validation rules** (length, links, placeholders, forbidden phrases)
  - `email_drafts` insert
- [ ] Email template assets (`PHYSICAL_ADDRESS`, signature)
- [ ] Unit test (mock LLM)
- [ ] Integration test (gold set: 5 örnek, TR + EN mix)

---

## 12. Suppression Service ⚠️ KRİTİK

- [ ] `SuppressionService`
  - `isSuppressed(email)` — pre-send check
  - `suppress(email, reason, sourceSendId)` — ekle
  - `unsuppress(email)` — admin manuel (rare)
  - Cache (Caffeine) — performans için
- [ ] Unit test (suppression flow)

---

## 13. Volume Limiter Service ⚠️ KRİTİK

- [ ] `VolumeLimiterService`
  - `getDailyCap()` — hesap yaşına göre warming progression
  - `canSendNow()` — bugünkü gönderim < cap mı?
  - `recordSend()` — counter increment
- [ ] Hesap yaşı config'ten (`account.created_at`)
- [ ] Unit test

---

## 14. SMTP Sender Service

- [ ] `SmtpService` (Spring Mail)
  - Gmail SMTP config (smtp.gmail.com:587, TLS, app password)
  - Connection pool: 1
  - Headers: List-Unsubscribe, X-Mailer, Message-ID
  - Tracking pixel inject (transparent 1x1)
  - Unsubscribe link inject (HMAC token)
- [ ] `EmailSendOrchestrator`:
  1. Suppression check
  2. Volume limit check
  3. SMTP send
  4. Volume increment
  5. `email_sends` status update
- [ ] Local test: MailHog ile gönderim
- [ ] Smoke test: Gerçek Gmail → kendi mail'ine

---

## 15. Bounce Tracker

- [ ] `BounceTrackerService`
  - IMAP poll (smtp.gmail.com IMAP, 5dk interval)
  - Last 1 hour mail filter
  - "Mail Delivery Subsystem" pattern match
  - Bounce code extraction (5.1.1, 5.7.1, etc.)
  - `email_bounces` insert
  - Hard bounce → `suppression_list` add
- [ ] Spring Scheduler (`@Scheduled(fixedDelay = 5min)`)
- [ ] Unit test (mock IMAP)

---

## 16. Reply Tracker

- [ ] `ReplyTrackerService`
  - IMAP poll
  - In-Reply-To header → original `email_sends` lookup
  - `email_replies` insert
  - Slack notification: "🎉 Reply from {company}"
- [ ] Unit test

---

## 17. Unsubscribe Service

- [ ] `UnsubscribeController`
  - `GET /unsubscribe?token=...` (signed HMAC)
  - Token verify
  - `suppression_list` add
  - "Mailing list'ten çıkarıldınız" sayfası (basit HTML)
- [ ] HMAC token generator (signed with secret)
- [ ] Unit test

---

## 18. REST Controllers

- [ ] `/api/v1/companies`
  - `POST /discover` (N8N tetikleyici)
  - `POST /import-csv`
  - `GET /` — list (filter: status, industry, country)
  - `GET /{id}` — detay
  - `POST /{id}/blacklist` — manual blacklist
- [ ] `/api/v1/email-accounts`
  - `GET /by-company/{companyId}`
- [ ] `/api/v1/drafts`
  - `GET /pending` — onay bekleyenler
  - `GET /{id}` — detay
  - `PUT /{id}/approve` — onayla (edited subject/body optional)
  - `PUT /{id}/reject` — reddet (reason)
- [ ] `/api/v1/sends`
  - `GET /` — list (filter: status, date)
  - `GET /{id}` — detay
- [ ] `/api/v1/replies`
  - `GET /unhandled`
  - `PUT /{id}/mark-handled`
- [ ] `/api/v1/suppression`
  - `GET /` — list
  - `POST /` — manuel ekle
- [ ] `/api/v1/discovery-filters` — CRUD
- [ ] `/api/v1/analytics/summary`:
  - Today: sent, bounced, replied, opened
  - 7d trend
- [ ] OpenAPI/Swagger UI
- [ ] CORS config (Vercel domain)

---

## 19. Slack Notification

- [ ] `SlackNotificationService` (async)
- [ ] Tetikleyiciler:
  - Yeni draft: "📝 5 new drafts pending approval"
  - Reply: "🎉 Reply from {company}!"
  - Bounce rate alert: "⚠️ Bounce rate %5+ (last 24h)"
  - SMTP error: "🚨 SMTP error: {details}"
- [ ] Webhook URL config'ten

---

## 20. N8N Workflows

- [ ] **`company-discovery` workflow:**
  - Cron: günlük 09:00 UTC
  - HTTP: Spring `/api/v1/discovery-filters` (aktif filtreler)
  - Loop: her filter için
  - HTTP: Spring `/api/v1/companies/discover` (filter ile tetikle)
- [ ] **`pipeline-process` workflow:**
  - Webhook tetikleyici (yeni company sonrası)
  - HTTP: Spring `/agent/extract-emails`
  - HTTP: Spring `/agent/analyze`
  - IF: BLACKLISTED? → END
  - Loop: her email_account için
  - HTTP: Spring `/agent/write`
  - (Slack notification Spring Boot tetikler, redundant değil)
- [ ] **`error-handler` sub-workflow:**
  - Tüm error path'leri Slack'e
- [ ] Workflow'ları JSON export, `workflows/` commit

---

## 21. Next.js Dashboard — İskelet

- [ ] `apps/web/` Next.js 14 init (App Router, TS, Tailwind)
- [ ] shadcn/ui kurulum
- [ ] NextAuth.js v5 — credentials provider (Akın için)
- [ ] API client (typed fetch wrapper)
- [ ] Layout: sidebar + main + topbar (notification icon)
- [ ] Theme: dark/light toggle

---

## 22. Dashboard Sayfaları

- [ ] `/login`
- [ ] `/` — ana:
  - Today: sent, opened, replied, bounced count
  - Pending drafts count
  - Recent replies (top 3)
  - Volume gauge (X/Y today)
- [ ] `/companies` — liste, filter
- [ ] `/companies/[id]` — detay (analysis JSON, email accounts)
- [ ] `/drafts` — pending onaylar liste
- [ ] `/drafts/[id]` — **EN KRİTİK**:
  - Şirket bilgileri (sol)
  - Email subject + body editor (sağ)
  - Personalization signals
  - "Approve & Send", "Edit", "Reject" buton'lar
  - Send'den sonra: `/sent/{id}`'e redirect
- [ ] `/sent` — gönderilenler, filter (status: sent/bounced/replied/opened)
- [ ] `/sent/[id]` — detay + timeline (sent → opened → replied)
- [ ] `/replies` — gelen yanıtlar
- [ ] `/replies/[id]` — yanıt body + original send + Akın'ın notu
- [ ] `/suppression` — read-only liste + manuel ekleme form
- [ ] `/settings` — discovery filters CRUD + sender info

---

## 23. Test Coverage

- [ ] Backend unit test coverage > %70
- [ ] Backend integration test:
  - Full pipeline (discovery → analyze → write → approve → send to MailHog)
  - Suppression flow (bounce → suppress → block next send)
  - Volume limit (cap exceeded → reject send)
- [ ] Frontend smoke test (Playwright):
  - Login → drafts → detail → approve → sent

---

## 24. Pre-Production Hardening

- [ ] **Rate limit testleri:**
  - Gmail SMTP: 5 email back-to-back
  - Spring Boot rate limit: 100 req/min
- [ ] **Email content testleri:**
  - HTML email render (Litmus alternatifleri, free)
  - Plain text fallback works
  - Unsubscribe link tıklanabilir
- [ ] **Spam score testi:**
  - mail-tester.com (free, ücretsiz)
  - Hedef: 8+/10 score
- [ ] **DNS records (Faz 2'de domain alındığında):**
  - SPF, DKIM, DMARC

---

## 25. Deployment

- [ ] **Spring Boot → Render:**
  - Dockerfile (multi-stage)
  - Web service oluştur
  - Env vars
- [ ] **N8N → Render:**
  - Mevcut instance kullan (varsa)
- [ ] **Next.js → Vercel:**
  - GitHub integration
  - Env vars
- [ ] **Postgres → Neon:**
  - Production branch
  - Migrations run

---

## 26. Smoke Test (canlı)

- [ ] N8N discovery manuel tetikle
- [ ] DB'de yeni company geldi mi
- [ ] Email extraction çalıştı mı
- [ ] Analyzer çalıştı mı (analysis JSON dolu mu)
- [ ] Writer çalıştı mı (draft var mı)
- [ ] Slack notification geldi mi
- [ ] Dashboard'da draft görünüyor mu
- [ ] **TEST EMAIL (kendi mail'ine):** Bir tane onayla, kendi gmail'ine gönder, geldi mi
- [ ] Tracking pixel açıldı mı (open kayıtlandı mı)
- [ ] Unsubscribe link tıkla, suppression eklendi mi
- [ ] **Hesap warming kontrol:** Volume cap doğru mu (yeni hesap → 5/gün)

---

## 27. İlk Hafta Operasyonu

- [ ] **Gün 1-3:** Sadece kendi alternatif email'lerine test gönderim (5/gün)
  - Spam'a düşüyor mu kontrol
  - Open/reply tracking çalışıyor mu
- [ ] **Gün 4-7:** İlk gerçek prospect'e gönderim
  - Çok dikkatli seç (ürünlerine en uygun olanlar)
  - Akın elle düzelt, AI'a güvenme tam
- [ ] Bounce rate izle (%2 üstü ise dur)
- [ ] Reply geldiğinde Akın yanıtlar (manual)

---

## 28. Faz 1 Closing

- [ ] 1 hafta operasyonel test
- [ ] Metrics:
  - Toplam gönderilen: ?
  - Bounce rate: ?
  - Open rate: ?
  - Reply rate: ?
- [ ] AI cost analysis (Groq token usage)
- [ ] Akın geri bildirimi
- [ ] Faz 2 plan (`docs/PHASE2_PLAN.md`):
  - Custom domain + Resend migration
  - Multi-language expansion
  - Reply auto-classifier
  - Reddit listener (back-burner'dan)

---

## Karar Noktaları (yol boyunca Akın'a sorulacak)

1. **Task 19 (NextAuth):** Sadece credentials mı, yoksa GitHub OAuth mı?
2. **Task 22 (`/drafts/[id]` editor):** Markdown editor mi, rich-text mi (Tiptap)?
3. **Task 25 (custom domain):** `outreach.akincoskun.dev` ister misin? (Faz 1'de gerek yok ama dashboard için olabilir)
4. **Task 27 (ilk gerçek prospect'ler):** İlk 10 şirketi Akın elle seçer mi, yoksa Discovery'nin random output'u mu?

---

## Dependencies Tree

```
0. Hazırlık (Akın)
    ↓
1. Repo init
    ↓
2. Local dev (Docker Compose)
    ↓
3. Spring Boot iskelet
    ↓
4. DB migrations
    ↓
5. Entities + repos
    ↓
6. DTO + MapStruct
    ↓
7. AI integration ────────┐
    ↓                     │
8. Discovery layer        │
    ↓                     │
9. Email extraction       │
    ↓                     │
10. Analyzer agent        │
    ↓                     │
11. Writer agent          │
    ↓                     │
12. Suppression ⚠️         │
    ↓                     │
13. Volume limiter ⚠️      │
    ↓                     │
14. SMTP sender ──────────┤  (paralel: 19 Slack)
    ↓                     │
15. Bounce tracker        │
    ↓                     │
16. Reply tracker         │
    ↓                     │
17. Unsubscribe           │
    ↓                     │
18. REST controllers      │
    ↓                     │
20. N8N workflows ────────┤
                          │
21. Next.js iskelet ──────┤
    ↓                     │
22. Dashboard sayfaları ──┘
    ↓
23. Tests
    ↓
24. Pre-prod hardening
    ↓
25. Deploy
    ↓
26. Smoke test
    ↓
27. İlk hafta operasyon
    ↓
28. Faz 1 closing
```

---

## Estimated Time

- **Senior dev (Akın), part-time:** 80-100 saat (4-5 hafta)
- **Claude Code agent:** 12-15 saat aktif sessions

Bunlar **realist baseline**. Akın'ın diğer Upwork işleri ile paralel ilerleyeceği için kendine baskı yapma.
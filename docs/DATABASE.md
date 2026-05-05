# DATABASE.md — AI Email Outreach Engine

## Veritabanı Genel Bakış

- **DB:** Neon PostgreSQL 16+
- **Extension'lar:** `pgvector`, `pg_trgm`, `pgcrypto` (HMAC tokens için)
- **Migration:** Flyway (versiyonlanmış SQL)
- **Connection pool:** HikariCP, max 10
- **Naming:** `snake_case`
- **Charset:** UTF-8
- **Timezone:** UTC

## Şema Diyagramı (yüksek seviye)

```
┌──────────────┐      ┌──────────────┐       ┌──────────────┐
│  companies   │      │email_accounts│       │email_drafts  │
│              │      │              │       │              │
│ id (UUID) PK │◄─────┤ company_id   │◄──────┤ company_id   │
│ domain       │      │ email        │       │ email_acct_id│
│ name         │      │ prefix_type  │       │ subject      │
│ analysis     │      │ ...          │       │ body         │
│ status       │      └──────────────┘       │ status       │
└──────────────┘                              └──────┬───────┘
                                                     │
                                              ┌──────▼───────┐
                                              │email_sends   │
                                              │              │
                                              │ id (UUID) PK │
                                              │ draft_id     │
                                              │ status       │
                                              │ sent_at      │
                                              └──────┬───────┘
                                                     │
                                       ┌─────────────┼─────────────┐
                                       │             │             │
                                ┌──────▼──┐    ┌─────▼───┐   ┌────▼───┐
                                │bounces  │    │opens    │   │replies │
                                │         │    │         │   │        │
                                │send_id  │    │send_id  │   │send_id │
                                │type     │    │opened_at│   │body    │
                                └─────────┘    └─────────┘   └────────┘

┌──────────────┐      ┌──────────────┐       ┌──────────────┐
│suppression_  │      │ ai_calls     │       │volume_log    │
│  list        │      │              │       │              │
│              │      │ id (UUID) PK │       │ id (UUID) PK │
│ id (UUID) PK │      │ provider     │       │ sent_date    │
│ email        │      │ tokens       │       │ count        │
│ reason       │      │ duration_ms  │       │ ...          │
└──────────────┘      └──────────────┘       └──────────────┘
```

## Tablolar — Detaylı

### 1. `companies`

Sistemin keşfettiği şirketler.

```sql
CREATE TABLE companies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identity
    domain          VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    website_url     VARCHAR(512),

    -- Discovery source
    source          VARCHAR(64) NOT NULL,  -- 'google_maps', 'crunchbase', 'manual_csv', 'sitemap_scrape'
    source_metadata JSONB,                 -- API response details
    discovered_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Geo
    country_code    CHAR(2),               -- 'TR', 'US', 'GB', vb.
    city            VARCHAR(128),

    -- AI Analysis (Analyzer Agent çıktısı)
    analysis        JSONB,                 -- {industry, size_estimate, problems, ...}
    analysis_at     TIMESTAMPTZ,
    analysis_version VARCHAR(32),          -- 'analyzer_v1'

    -- Pipeline state
    status          VARCHAR(32) NOT NULL DEFAULT 'NEW',
    -- NEW, EMAILS_EXTRACTED, ANALYZED, DRAFT_READY, SENT, BLACKLISTED, ARCHIVED
    status_reason   TEXT,

    -- Embedding (Faz 2'de matching için)
    embedding       vector(384),

    -- Bookkeeping
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at     TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_companies_status ON companies(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_companies_domain ON companies(domain) WHERE deleted_at IS NULL;
CREATE INDEX idx_companies_country ON companies(country_code);
CREATE INDEX idx_companies_analysis ON companies USING gin (analysis);
CREATE INDEX idx_companies_embedding ON companies USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

### 2. `email_accounts`

Bir şirketin jenerik kurumsal email adresleri.

```sql
CREATE TABLE email_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,

    email           VARCHAR(255) NOT NULL,
    prefix_type     VARCHAR(32) NOT NULL,  -- 'info', 'contact', 'kariyer', 'hr', 'sales', 'support', vb.
    extracted_from  VARCHAR(512),          -- URL where email was found

    -- Validation
    is_valid_format BOOLEAN NOT NULL DEFAULT TRUE,
    is_generic      BOOLEAN NOT NULL DEFAULT TRUE,  -- whitelist regex matched
    validation_note TEXT,

    -- Bookkeeping
    extracted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT email_accounts_unique UNIQUE (company_id, email)
);

CREATE INDEX idx_email_accounts_company ON email_accounts(company_id);
CREATE INDEX idx_email_accounts_email ON email_accounts(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_email_accounts_prefix ON email_accounts(prefix_type);
```

**Whitelist prefix'leri (validation):**
- `info`, `contact`, `hello`, `hi`, `office`
- `kariyer`, `career`, `careers`, `jobs`, `recruitment`
- `hr`, `ik`, `humanresources`
- `sales`, `business`, `partnerships`
- `support`, `destek`, `help`
- `bilgi`, `iletisim`, `reception`
- `admin`, `team`

**Otomatik filtrelenen pattern'ler (kişi-spesifik):**
- `firstname.lastname@`
- `firstname-lastname@`
- `f.lastname@`
- Tek isim kelimesi: `ahmet@`, `john@`, etc.

### 3. `email_drafts`

AI Writer'ın oluşturduğu email taslakları.

```sql
CREATE TABLE email_drafts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    email_account_id UUID NOT NULL REFERENCES email_accounts(id) ON DELETE CASCADE,

    -- Email content
    subject         VARCHAR(255) NOT NULL,
    body_html       TEXT NOT NULL,
    body_text       TEXT NOT NULL,
    language        CHAR(2) NOT NULL,      -- 'tr', 'en'

    -- AI metadata
    model_used      VARCHAR(64),
    prompt_version  VARCHAR(32),
    personalization_signals JSONB,         -- ["industry", "language", "specific_problem"]
    warnings        JSONB,                 -- ["too_generic", "too_long"]

    -- Akın'ın etkileşimi
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    -- PENDING, APPROVED, REJECTED, EDITED, SENT
    edited_subject  VARCHAR(255),
    edited_body_html TEXT,
    edited_body_text TEXT,
    rejection_reason TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at     TIMESTAMPTZ,
    rejected_at     TIMESTAMPTZ
);

CREATE INDEX idx_email_drafts_company ON email_drafts(company_id);
CREATE INDEX idx_email_drafts_status ON email_drafts(status);
CREATE INDEX idx_email_drafts_pending ON email_drafts(created_at DESC) WHERE status = 'PENDING';
```

### 4. `email_sends`

Gönderilen email'ler.

```sql
CREATE TABLE email_sends (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id        UUID NOT NULL REFERENCES email_drafts(id) ON DELETE RESTRICT,
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,

    -- Email metadata
    to_email        VARCHAR(255) NOT NULL,
    from_email      VARCHAR(255) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    message_id      VARCHAR(255) UNIQUE,   -- SMTP Message-ID header

    -- Send state
    status          VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    -- QUEUED, SENDING, SENT, FAILED, BOUNCED, SUPPRESSED
    smtp_response   TEXT,
    error_message   TEXT,
    retry_count     INTEGER NOT NULL DEFAULT 0,

    -- Tracking
    tracking_pixel_token VARCHAR(64),       -- HMAC signed
    unsubscribe_token    VARCHAR(64),       -- HMAC signed

    -- Timing
    queued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ
);

CREATE INDEX idx_email_sends_status ON email_sends(status);
CREATE INDEX idx_email_sends_to_email ON email_sends(to_email);
CREATE INDEX idx_email_sends_message_id ON email_sends(message_id);
CREATE INDEX idx_email_sends_sent_at ON email_sends(sent_at DESC);
CREATE INDEX idx_email_sends_unsub_token ON email_sends(unsubscribe_token);
CREATE INDEX idx_email_sends_pixel_token ON email_sends(tracking_pixel_token);
```

### 5. `email_opens`

Tracking pixel açılışları (open rate).

```sql
CREATE TABLE email_opens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    send_id         UUID NOT NULL REFERENCES email_sends(id) ON DELETE CASCADE,

    opened_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_agent      VARCHAR(512),
    ip_country_code CHAR(2)                 -- IP'den anonim ülke (privacy)
);

CREATE INDEX idx_email_opens_send ON email_opens(send_id);
CREATE INDEX idx_email_opens_opened_at ON email_opens(opened_at DESC);
```

### 6. `email_bounces`

Geri gelen email'ler.

```sql
CREATE TABLE email_bounces (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    send_id         UUID NOT NULL REFERENCES email_sends(id) ON DELETE CASCADE,

    bounce_type     VARCHAR(32) NOT NULL,   -- 'hard', 'soft', 'complaint'
    bounce_reason   TEXT,                    -- "550 5.1.1 No such user"
    bounce_code     VARCHAR(16),             -- "5.1.1"
    raw_message     TEXT,                    -- Full bounce email (truncated)

    detected_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_bounces_send ON email_bounces(send_id);
CREATE INDEX idx_email_bounces_type ON email_bounces(bounce_type);
```

### 7. `email_replies`

Şirketten gelen yanıtlar.

```sql
CREATE TABLE email_replies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    send_id         UUID REFERENCES email_sends(id) ON DELETE SET NULL,

    -- Reply email
    from_email      VARCHAR(255) NOT NULL,
    subject         VARCHAR(255),
    body_text       TEXT,
    in_reply_to     VARCHAR(255),            -- Reference to original Message-ID

    -- Classification (Faz 2: AI ile)
    classification  VARCHAR(32),             -- 'interested', 'not_interested', 'oof', 'unsubscribe', 'unknown'
    is_handled      BOOLEAN NOT NULL DEFAULT FALSE,

    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_replies_send ON email_replies(send_id);
CREATE INDEX idx_email_replies_unhandled ON email_replies(received_at DESC) WHERE is_handled = FALSE;
```

### 8. `suppression_list` ⚠️ KRİTİK

**Bu tablo bir email'e bir daha mail atılmamasını sağlar. Pre-send hook bypass edilemez.**

```sql
CREATE TABLE suppression_list (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,

    reason          VARCHAR(64) NOT NULL,
    -- 'hard_bounce', 'soft_bounce_3x', 'unsubscribe', 'complaint', 'manual_block'
    source_send_id  UUID REFERENCES email_sends(id) ON DELETE SET NULL,
    notes           TEXT,

    suppressed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ          -- NULL = kalıcı
);

CREATE INDEX idx_suppression_email ON suppression_list(email);
CREATE INDEX idx_suppression_active ON suppression_list(email) WHERE expires_at IS NULL OR expires_at > NOW();
```

### 9. `volume_log`

Günlük gönderim sayacı.

```sql
CREATE TABLE volume_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sent_date       DATE NOT NULL UNIQUE,
    sent_count      INTEGER NOT NULL DEFAULT 0,
    daily_cap       INTEGER NOT NULL,         -- O günün cap'ı (warming progression)

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_volume_log_date ON volume_log(sent_date DESC);
```

### 10. `ai_calls`

Tüm AI çağrılarının logu.

```sql
CREATE TABLE ai_calls (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Bağlam
    company_id      UUID REFERENCES companies(id) ON DELETE SET NULL,
    agent_name      VARCHAR(32) NOT NULL,    -- 'analyzer', 'writer'

    -- Provider
    provider        VARCHAR(32) NOT NULL,    -- 'groq', 'gemini', 'huggingface'
    model           VARCHAR(64) NOT NULL,
    prompt_version  VARCHAR(32),

    -- Cost
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    total_tokens    INTEGER GENERATED ALWAYS AS (COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)) STORED,
    duration_ms     INTEGER,

    -- Result
    success         BOOLEAN NOT NULL,
    error_message   TEXT,

    -- Debug
    prompt_snippet  VARCHAR(512),
    response_snippet VARCHAR(512),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_calls_company ON ai_calls(company_id);
CREATE INDEX idx_ai_calls_agent ON ai_calls(agent_name);
CREATE INDEX idx_ai_calls_created_at ON ai_calls(created_at DESC);
CREATE INDEX idx_ai_calls_success ON ai_calls(success) WHERE success = FALSE;
```

### 11. `pipeline_runs`

N8N workflow çalışma logları.

```sql
CREATE TABLE pipeline_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_name   VARCHAR(64) NOT NULL,
    n8n_execution_id VARCHAR(64),

    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    duration_ms     INTEGER,

    status          VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    items_processed INTEGER DEFAULT 0,
    items_succeeded INTEGER DEFAULT 0,
    items_failed    INTEGER DEFAULT 0,

    error_summary   TEXT,
    metadata        JSONB
);

CREATE INDEX idx_pipeline_runs_workflow ON pipeline_runs(workflow_name);
CREATE INDEX idx_pipeline_runs_started_at ON pipeline_runs(started_at DESC);
```

### 12. `discovery_filters`

Akın'ın kullandığı sektör/lokasyon filtreleri (settings).

```sql
CREATE TABLE discovery_filters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(128) NOT NULL,        -- "Istanbul restaurants"
    industry        VARCHAR(64),                   -- "restaurant"
    country_code    CHAR(2),
    city            VARCHAR(128),
    keywords        TEXT[],                        -- additional search terms
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_discovery_filters_active ON discovery_filters(is_active);
```

## İlişkiler — Cascade Davranışı

| Parent | Child | OnDelete |
|---|---|---|
| `companies` | `email_accounts` | CASCADE |
| `companies` | `email_drafts` | CASCADE |
| `email_accounts` | `email_drafts` | CASCADE |
| `email_drafts` | `email_sends` | RESTRICT (kayıt korunsun) |
| `email_sends` | `email_opens` | CASCADE |
| `email_sends` | `email_bounces` | CASCADE |
| `email_sends` | `email_replies` | SET NULL |
| `companies` | `ai_calls` | SET NULL |
| `email_sends` | `suppression_list` | SET NULL |

## Migration Stratejisi

**Versiyonlama:** Flyway, `V{number}__{description}.sql`

**İlk migrations:**
```
V1__enable_extensions.sql           -- pgvector, pg_trgm, pgcrypto
V2__create_companies.sql
V3__create_email_accounts.sql
V4__create_email_drafts.sql
V5__create_email_sends.sql
V6__create_email_opens.sql
V7__create_email_bounces.sql
V8__create_email_replies.sql
V9__create_suppression_list.sql
V10__create_volume_log.sql
V11__create_ai_calls.sql
V12__create_pipeline_runs.sql
V13__create_discovery_filters.sql
V14__seed_initial_data.sql          -- Akın'ın başlangıç filtreleri
```

**Seed data örneği:**

```sql
-- Faz 1 başlangıç filtreleri
INSERT INTO discovery_filters (name, industry, country_code, city, keywords) VALUES
('Istanbul Restaurants', 'restaurant', 'TR', 'Istanbul', ARRAY['cafe', 'lokanta']),
('NYC SaaS Startups', 'saas', 'US', 'New York', ARRAY['startup', 'b2b']),
('London Marketing Agencies', 'marketing', 'GB', 'London', ARRAY['agency', 'digital']);

-- İlk volume_log entry (warming starts at 0)
INSERT INTO volume_log (sent_date, sent_count, daily_cap) VALUES
(CURRENT_DATE, 0, 0);  -- Account warming yet
```

## İndeks Stratejisi — Önemli Notlar

**Kritik query patterns:**
- "Bekleyen draft'lar" → `idx_email_drafts_pending`
- "Bugünkü gönderimler" → `idx_email_sends_sent_at`
- "Yanıtlanmamış reply'lar" → `idx_email_replies_unhandled`
- "Suppression check" → `idx_suppression_active` (her send öncesi)

**pgvector:**
- `companies.embedding` IVFFlat index
- Faz 2'de "benzer şirket" özelliği için kullanılacak

**JSONB:**
- `companies.analysis` GIN index → "tüm restoranları getir" gibi sorgular hızlı

## Veri Saklama Politikası

| Tablo | Saklama | Stratejisi |
|---|---|---|
| `companies` (NEW, BLACKLISTED) | 90 gün | Hard delete |
| `companies` (SENT, REPLIED) | 365 gün | Soft archive |
| `email_drafts` (REJECTED) | 30 gün | Hard delete |
| `email_drafts` (SENT) | 365 gün | Soft archive |
| `email_sends` | Sonsuz | Saklanır (compliance + öğrenme) |
| `email_opens`, `email_bounces`, `email_replies` | Sonsuz | Saklanır |
| `suppression_list` | **Sonsuz, hiç silinmez** | KRİTİK — kalıcı kayıt |
| `volume_log` | Sonsuz | Saklanır |
| `ai_calls` | 90 gün | Hard delete |
| `pipeline_runs` | 30 gün | Hard delete |

**Cleanup job:** Spring Boot Scheduled, gece 03:00 UTC.

## Performans Notları

**Beklenen yük (Faz 1, warming sonrası):**
- 100-200 company discovery/gün
- 50-100 AI call/gün
- 30-50 email_send/gün
- 1-3 reply/gün

**Bottleneck adayları:**
- Suppression check (her send öncesi) → çözüm: index doğru, query basit
- Bounce IMAP poll → çözüm: yalnızca son 1 saatlik mail tarama
- AI rate limit → çözüm: Resilience4j circuit breaker

## Veri Güvenliği

**PII (Personally Identifiable Information):**
- Toplanan tüm "data" jenerik kurumsal email
- Kişi adı içeren email otomatik filtrelenir (extraction'da)
- Reply body sınırlı süre saklanır (90 gün)
- Open tracking IP'sinden sadece anonim ülke kodu çıkarılır

**Backup:**
- Neon otomatik daily backup (free tier)
- Suppression list ayrıca haftalık CSV export (Akın'ın email'ine)

**Encryption:**
- Neon: at-rest encrypted by default
- Connection: TLS zorunlu
- HMAC tokens (unsubscribe, tracking pixel) — secret key env var'da

## Compliance Audit Trail

Her **email_send** kaydı şunlara cevap verir (denetlenebilir):
- Bu email'i kim onayladı? (Akın, draft.approved_at)
- Hangi içerik gönderildi? (subject, body_html — değişmiyor)
- Suppression check yapıldı mı? (her send'in pre-condition'ı)
- Bounce/unsub aksiyonu alındı mı? (related table'lara bakılır)

Bu audit trail GDPR Article 5 (accountability) için yeterli.
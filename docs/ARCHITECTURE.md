# ARCHITECTURE.md — AI Email Outreach Engine

## Sistem Genel Bakış

AI Email Outreach Engine, **N8N orchestrator + Spring Boot worker + Next.js dashboard** üçlüsünden oluşan bir B2B email outreach sistemidir. Şirketleri keşfeder, sitelerini analiz eder, kişiselleştirilmiş email'ler yazar ve Akın'ın onayıyla SMTP üzerinden gönderir.

## Mimari Prensipleri

1. **N8N = Orchestrator** — Discovery scheduling, retry, conditional routing, webhook
2. **Spring Boot = Worker** — Business logic, AI calls, SMTP, database I/O
3. **Next.js = Cockpit** — Akın'ın email'leri gördüğü, onayladığı UI
4. **Postgres = Truth** — Tek source of truth, tüm state burada
5. **Suppression List = Sacred** — Her gönderim öncesi ZORUNLU kontrol

## Yüksek Seviye Akış

```
┌───────────────────────────────────────────────────────────────────────┐
│                  N8N: Discovery Workflow                                │
│  • Cron: günlük 09:00 UTC                                              │
│  • Sektör/lokasyon filtresine göre Google Maps API                     │
│  • Crunchbase free API (startup'lar için)                              │
└──────────────────────────────┬────────────────────────────────────────┘
                               │ HTTP POST
                               ▼
┌───────────────────────────────────────────────────────────────────────┐
│           Spring Boot: POST /api/v1/companies/discover                 │
│  • Company DB'ye yazılır (status: NEW)                                 │
│  • Duplicate kontrol (domain unique)                                   │
└──────────────────────────────┬────────────────────────────────────────┘
                               │ N8N webhook
                               ▼
┌───────────────────────────────────────────────────────────────────────┐
│                  N8N: Email Extraction Workflow                         │
│  • Şirketin web sitesini fetch eder                                    │
│  • Cheerio ile parse                                                   │
│  • Whitelist regex: SADECE jenerik prefix'ler                          │
│    (info, contact, hello, hi, kariyer, career, jobs, hr, ik,           │
│     sales, support, admin)                                             │
│  • Bulunan email'ler EmailAccount tablosuna yazılır                    │
└──────────────────────────────┬────────────────────────────────────────┘
                               │
                               ▼
┌───────────────────────────────────────────────────────────────────────┐
│                  N8N: Pipeline Workflow                                 │
│  • Status=NEW companies çekilir                                        │
│  • Her biri sırayla agent'lardan geçirilir                            │
└──────────┬───────────────┬───────────────────────────────────────────┘
           │               │
           ▼               ▼
   ┌──────────┐    ┌──────────┐
   │Analyzer  │    │ Writer   │
   │Agent     │    │ Agent    │
   │(AI #1)   │    │ (AI #2)  │
   └──────────┘    └──────────┘
           │               │
           └───────┬───────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Postgres            │
        │  (status: REVIEWED)  │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Slack Notification  │
        │  + Dashboard URL     │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Akın approves /     │
        │  edits / rejects     │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Suppression Check   │
        │  (Bounce, Unsub,     │
        │   Complaint listesi) │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Volume Limiter      │
        │  (günlük cap)        │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  SMTP Sender         │
        │  (Gmail SMTP)        │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Bounce + Reply      │
        │  Tracker (IMAP poll) │
        └──────────────────────┘
```

## Agent Tasarımı

Her agent **Spring Boot servisi** olarak yazılır, **N8N webhook'tan tetiklenir**, **stateless** çalışır.

### 1. Analyzer Agent

**Sorumluluk:** Şirket sitesinin içeriğini AI ile analiz et, B2B profil çıkar.

**Input:** Company (name, domain, raw_html_snippet)
**Output:**
```json
{
  "industry": "restaurant",
  "sub_industry": "italian_food",
  "size_estimate": "small",
  "country_hint": "TR",
  "primary_language": "tr",
  "tech_stack_hints": ["wordpress", "instagram"],
  "potential_problems": [
    "no online ordering",
    "no chatbot for FAQs",
    "manual reservation"
  ],
  "target_audience": "consumers",
  "online_presence_score": 0.6
}
```

**Implementation:**
- Spring Boot fetch eder şirket ana sayfasını + /about + /contact + /services
- Jsoup ile HTML temizlenir, body text 5000 karaktere truncate
- Groq Llama 3.3 ile structured JSON output
- Fallback: Gemini Flash 2.0
- Result `companies.analysis` JSONB kolonuna yazılır

### 2. Writer Agent

**Sorumluluk:** Her şirket için **portfolio overview** içeren kişiselleştirilmiş email yaz.

**Input:** Company analysis + EmailAccount + Akın profile + Products (hardcoded list, 7 ürün)
**Output:**
```json
{
  "subject": "Tools for [restaurant/saas/agency] businesses",
  "body_html": "...",
  "body_text": "...",
  "char_count": 1200,
  "personalization_signals": ["industry mentioned", "country language", "specific problem"],
  "warnings": [],
  "language": "tr"
}
```

**Implementation:**
- Şirket dilini analyzer çıktısından alır (TR/EN)
- Hangi ürünleri vurgulayacağını analyzer çıktısından infer eder (örn: restoran → AI Chatbot ön planda, KolayAidat alakasız)
- 7 ürünü hepsi portfolio'da listeler ama 1-2 tanesini "size özellikle uygun olabilir" diye öne çıkarır
- Akın'ın imza bilgisi (isim, GitHub, portfolio URL, fiziksel adres - CAN-SPAM)
- Unsubscribe link otomatik eklenir
- HTML + plain text version

## Spring Boot Servisleri (Agent dışı)

### 3. CompanyDiscoveryService

**Sorumluluk:** Yeni şirketler bul.

**Stratejiler:**
- **Google Maps API** — sektör + lokasyon filtresi (örn: "restaurant Istanbul")
- **Sitemap scraping** — bilinen B2B directory'lerden (Crunchbase, AngelList free)
- **Manual seed** — Akın bir CSV upload edebilir (settings sayfasından)

**Çıktı:** `companies` tablosuna upsert.

### 4. EmailExtractionService

**Sorumluluk:** Bir şirketin sitesinden jenerik kurumsal email'leri çıkar.

**Pipeline:**
1. Domain'den `https://{domain}/contact` ve `/iletisim` ve ana sayfa fetch
2. Cheerio/Jsoup ile email regex search
3. **Whitelist filter** — sadece jenerik prefix'ler:
   ```
   ^(info|contact|hello|hi|kariyer|career|careers|jobs|hr|ik|ir|
     sales|support|admin|office|reception|bilgi|destek)
     @
   ```
4. **Blacklist filter** — kişi adı içerenler (firstname.lastname pattern)
5. EmailAccount tablosuna kaydedilir

### 5. SuppressionService

**Sorumluluk:** Bir email'e mail atmadan önce **kontrol et**.

**Suppression sebepleri:**
- Hard bounce (kalıcı)
- Soft bounce (3 kez üst üste → kalıcı)
- Unsubscribe (kalıcı)
- Spam complaint (kalıcı)
- Manual block (Akın yasakladı)

**Implementation:** Pre-send hook, **bypass edilemez**. Her gönderim öncesi `suppression_list` tablosu sorgulanır.

### 6. VolumeLimiterService

**Sorumluluk:** Günlük gönderim limitini zorla.

**Mantık:**
```
hesap_yaşı < 14 gün → 0/gün (warming)
hafta 1-2 → 5/gün
hafta 3-4 → 10/gün
hafta 5-6 → 20/gün
hafta 7+ → 30-50/gün
```

**Implementation:** Pre-send hook, gün başında reset.

### 7. SmtpSenderService

**Sorumluluk:** Email'i SMTP üzerinden gönder.

**Ayarlar:**
- Gmail SMTP: `smtp.gmail.com:587` (TLS)
- App Password (2FA zorunlu)
- Connection pool: 1 (Gmail rate limit'e dikkat)
- Retry: hata durumunda 3 deneme, exponential backoff

**Header'lar:**
- `From: Akın Coşkun <akin.tools.outreach@gmail.com>`
- `Reply-To: same`
- `List-Unsubscribe: <mailto:unsub@...>, <https://.../unsubscribe?token=...>`
- `List-Unsubscribe-Post: List-Unsubscribe=One-Click`
- `X-Mailer: AI-Outreach-Engine/1.0`
- Custom tracking pixel (open rate)

### 8. BounceTrackerService

**Sorumluluk:** Geri gelen email'leri yakala, suppression'a ekle.

**Implementation:**
- IMAP poll: Gmail inbox'ı 5 dakikada bir tarar
- "Mail Delivery Subsystem" pattern'i match eder
- Hard bounce keyword'leri (550, 5.1.1, 5.1.10, NoSuchUser)
- İlgili email_send'i bulur, status'u BOUNCED yapar
- Hedef email'i suppression_list'e ekler

### 9. ReplyTrackerService

**Sorumluluk:** Şirketten gelen yanıtı yakala, Akın'a bildir.

**Implementation:**
- IMAP poll
- In-Reply-To header ile orijinal email_send'i bul
- Reply tablosuna kaydet
- Slack notification: "🎉 Reply from {company}!"
- Dashboard'da reply'a yönlendir

### 10. UnsubscribeService

**Sorumluluk:** Unsubscribe link'lerini handle et.

**Endpoint:** `GET /unsubscribe?token={signed_token}`
- Token'ı verify et (HMAC)
- İlgili email_account'u suppression_list'e ekle
- "Mailing list'ten çıkarıldınız" sayfası göster

## Dashboard (Next.js) Akışı

**Sayfalar:**
1. `/` — Dashboard ana sayfa (bekleyen onaylar, bugünkü gönderim, replies)
2. `/companies` — Şirketler listesi + filtre
3. `/companies/[id]` — Şirket detay + analiz + email taslakları
4. `/drafts` — Onay bekleyen email taslakları
5. `/drafts/[id]` — Email edit + onay
6. `/sent` — Gönderilmiş email'ler + status (delivered, bounced, replied)
7. `/replies` — Gelen yanıtlar
8. `/suppression` — Suppression list (read-only + manuel ekleme)
9. `/settings` — Sektör/lokasyon filtreleri, volume cap, sender info

**Akın'ın yolculuğu:**
```
1. Sabah Slack: "5 yeni email taslağı bekliyor"
2. Dashboard'a tıklar → /drafts
3. Birine tıklar:
   - Şirket bilgileri + analiz
   - Subject + body (editable)
   - Personalization signals görünür
   - "Approve & Send", "Edit", "Reject" butonları
4. Approve → suppression check → volume check → SMTP send
5. Sent listesinde görünür
6. Reply gelirse Slack alert + /replies sayfası
```

## State Machine — Email Send

```
DRAFT (AI yazdı)
  │
  ├──> APPROVED (Akın onayladı)
  │     │
  │     └──> QUEUED (volume cap'a girene kadar bekliyor)
  │           │
  │           └──> SENDING (SMTP'ye verildi)
  │                 │
  │                 ├──> SENT (delivery confirmation)
  │                 │     │
  │                 │     ├──> OPENED (tracking pixel)
  │                 │     │     │
  │                 │     │     └──> REPLIED (yanıt geldi)
  │                 │     │
  │                 │     └──> CLICKED (link tıklandı)
  │                 │
  │                 └──> FAILED (SMTP error)
  │
  ├──> REJECTED (Akın reddetti)
  │
  ├──> BOUNCED (geri geldi, hard bounce)
  │
  └──> SUPPRESSED (suppression list'e takıldı, gönderilmedi)
```

## Servis-Servis İletişimi

**N8N → Spring Boot:** HTTP webhook
- Authentication: Static API key
- Idempotency: `X-Idempotency-Key` header
- Timeout: 30s
- Retry: 3x exponential backoff

**Spring Boot → Groq/Gemini:** REST API
- Circuit breaker: Resilience4j
- Token tracking: ai_calls tablosuna yazılıyor
- Auto-fallback: Groq 5xx → Gemini

**Spring Boot → Gmail SMTP:** SMTPS over TLS
- App password (2FA)
- Single connection pool (rate limit awareness)
- Retry: 3x with exponential backoff

**Spring Boot → IMAP (Gmail):** Polling
- Bounce + reply detection
- 5 dakikada bir
- Sadece son 1 saatlik email'leri tarar (efficiency)

**Spring Boot → Slack:** Webhook
- Async (CompletableFuture)
- Failure tolerated

## Güvenlik & Compliance

**Email security:**
- App password (2FA hesapta zorunlu)
- SMTP credentials env var'da, repo'da değil
- IMAP credentials aynı

**Compliance kontrolleri (kod-level):**
1. **Suppression check** — bypass edilemez, send öncesi zorunlu
2. **Volume limit** — bypass edilemez, gün başında reset
3. **Unsubscribe link** — her email'de zorunlu (template'da hardcoded)
4. **Physical address** — sender footer'da hardcoded (CAN-SPAM)
5. **Identity** — sender adı + email truthful, spoofing yok
6. **Domain blacklist** — `.gov`, `.edu`, devlet kurumları skip
7. **Country blacklist** — Almanya, Fransa hariç tutulur (Faz 1)

**KVKK kontrolleri:**
- Toplanan tüm "data" kurumsal jenerik email
- Kişi adı içeren email otomatik filtrelenir
- 90 günden eski non-active company verileri archive
- Akın isteyince tüm data export + delete

## Observability

**Logging:**
- Spring Boot: Logback + JSON
- Sensitive data (tam email body) hash'leniyor
- Correlation ID

**Metrics:**
- Spring Boot Actuator + Micrometer
- Custom: emails_sent_total, bounce_rate, reply_rate
- Dashboard'da görünür

**Alerting:**
- Slack: bounce rate > %5, AI failure, SMTP error
- Email: günlük summary (Akın'a)

## Dağıtım

| Servis | Platform |
|---|---|
| `apps/api` (Spring Boot) | Render (Dockerfile) |
| `apps/web` (Next.js) | Vercel |
| N8N | Render (mevcut instance) |
| Postgres | Neon |
| SMTP | Gmail (yeni hesap) |

**Environment'lar:**
- `local` — Docker Compose
- `production` — yukarıdaki dağıtım

## Faz 2 ve Sonrası

- **Custom domain** + Resend (deliverability)
- **Engagement tracking** dashboard
- **A/B testing** (subject lines, body variants)
- **Reply auto-classifier** (interested / not interested / out of office)
- **Multi-account rotation** (volume artışı için)
- **Reddit listener** (back-burner'da, sonraki faz)
- **Türkçe Twitter listener** (KolayAidat/Çerezmatik için)

## Bilinçli Kararlar (Trade-off'lar)

1. **Gmail SMTP yerine Resend?**
   - **Karar:** Gmail (Faz 1) → Resend (Faz 2)
   - **Trade-off:** Hız vs profesyonellik. Faz 1'de hızlı validation öncelikli.

2. **Custom domain alınmadı**
   - **Karar:** Yeni Gmail hesabı yeterli (Faz 1)
   - **Trade-off:** Free vs deliverability. İlk 30 günde validate ederiz.

3. **MCP Server kullanmadık**
   - **Karar:** 7 ürün hardcoded list olarak Spring Boot'ta
   - **Trade-off:** Esneklik vs basitlik. 7 ürün nadiren değişiyor.

4. **Filter ve Matcher agent kaldırıldı**
   - **Karar:** Sadece Analyzer + Writer
   - **Trade-off:** Basitlik vs precision. Portfolio overview yaklaşımıyla matcher gereksiz.

5. **Almanya/Fransa hedeflenmedi**
   - **Karar:** Sıkı GDPR yorumları nedeniyle hariç
   - **Trade-off:** Kapsam vs risk. Hollanda, Belçika, Avusturya OK.

## Açık Sorular (Sonradan netleşecek)

1. **Tracking pixel kullanılsın mı?** — Open rate vs gizlilik.
   - Karar: Evet, opt-in transparent şekilde footer'da belirtilerek
2. **Reply'lara AI yanıt yazsın mı?**
   - Karar: Faz 1'de hayır, sadece Akın yanıtlar
3. **Multi-language support nasıl scale eder?**
   - Karar: TR + EN yeter Faz 1, Almanca/İspanyolca Faz 3
4. **CRM entegrasyonu?**
   - Karar: Faz 2'de düşünülecek (HubSpot, Notion, vb.)
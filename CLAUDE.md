# CLAUDE.md — AI Email Outreach Engine

> **Bu dosya Claude konuşmalarında context olarak yüklenmek üzere tasarlandı.**
> Yeni bir konuşma başlattığında bu dosyayı paylaş, Claude projeyi tam anlasın.

---

## Proje Kimliği

**Ad:** AI Email Outreach Engine
**Sahip:** Akın Coşkun ([github.com/akincskn](https://github.com/akincskn))
**Başlangıç:** Mayıs 2026
**Durum:** Faz 1 — Geliştirme aşaması

## Amaç

> Akın'ın geliştirdiği demo ürünleri (RivalRadar, AI Chatbot Platform, GEO Analyzer, LeadPilot, KolayAidat, FormJet, Çerezmatik) hakkında bilgilendirme amaçlı, **yurt içi ve yurt dışı şirketlerin jenerik kurumsal email adreslerine** (info@, kariyer@, hr@, contact@) **kişiselleştirilmiş, AI tarafından yazılmış email'ler** gönderen otomatize bir sistem.

**Bu sistem ne YAPAR:**
- ✅ Şirketleri bulur (Google Maps API + web scraping)
- ✅ **Sadece jenerik kurumsal email'leri** çıkarır (info@, kariyer@, hr@, contact@, hello@)
- ✅ Şirket sitesini AI ile analiz eder (sektör, büyüklük, problemler)
- ✅ Her şirket için **kişiselleştirilmiş email** yazar (AI Writer)
- ✅ Akın'a Slack/dashboard üzerinden onay sunar
- ✅ Onaylanan email'leri SMTP ile gönderir
- ✅ Bounce, reply, unsubscribe tracking yapar

**Bu sistem ne YAPMAZ:**
- ❌ Kişi-spesifik email'lere mail atmaz (sadece jenerik kurumsal)
- ❌ Hunter.io / LinkedIn scraping yapmaz
- ❌ Bulk template email atmaz (her email AI tarafından kişiselleştirilir)
- ❌ Onay almadan otomatik göndermez (Faz 1)

## Çalışma Felsefesi

**B2B kurumsal outreach:**
- Hedef: jenerik kurumsal email adresleri (kişisel veri DEĞİL)
- İçerik: "Akın olarak şu ürünleri yaptım, ihtiyacınız olabilir, bir ihtiyacınız var mı?"
- Ton: profesyonel ama insanca, satışçı değil
- Volume: kontrollü (warming sonrası günde 30-50)

**Yasal zemin:**
- KVKK: Jenerik kurumsal email'ler kişisel veri kapsamında değil
- 6563 sayılı Kanun: B2B mal/hizmet tedariki istisnası kapsamında
- GDPR: Article 6(1)(f) legitimate interest + opt-out + transparency
- CAN-SPAM (ABD): Truthful subject + unsubscribe + physical address
- **Avukat değiliz, ileride profesyonel KVKK uzmanı konsültasyonu önerilir**

## Faz 1 Kapsamı

**Hedef ürünler:** 7 ürünün **tümü** bir email'de tanıtılıyor (portfolio overview yaklaşımı)
- RivalRadar, AI Chatbot Platform, GEO Analyzer, LeadPilot, KolayAidat, FormJet, Çerezmatik

**Hedef coğrafya:**
- Yurt içi (Türkiye)
- Yurt dışı (öncelik: ABD, UK, Kanada, Hollanda, İskandinav ülkeleri)
- Almanya/Fransa **hariç tutulur** (sıkı GDPR yorumları)

**Email kanalı:** Yeni Gmail hesabı + SMTP (Faz 2'de Resend + custom domain'e migration)

**Diller:**
- Türkçe (TR şirketler)
- İngilizce (yurt dışı)

## Email Warming & Volume Stratejisi

```
Hafta 1-2  → 5 email/gün (yeni Gmail hesabı warm up)
Hafta 3-4  → 10 email/gün
Hafta 5-6  → 20 email/gün
Hafta 7+   → 30-50 email/gün (sürdürülebilir maximum)
```

**Kurallar:**
- Hesap yaşı min 2 hafta olmadan email atılmaz
- İlk hafta normal Gmail kullanımı yapılır (profil, telefon, normal mail)
- Hard bounce gelen adres → suppression list (kalıcı)
- Spam complaint → durdurma + analiz
- Unsubscribe → suppression list (kalıcı)

## Tech Stack

| Katman | Teknoloji | Neden |
|---|---|---|
| Orchestrator | N8N (Render) | Görsel workflow, retry, scheduling |
| Backend | Spring Boot 3.x (Java 21) | Akın'ın derinleştirdiği pattern, virtual threads |
| Frontend Dashboard | Next.js 14 + TypeScript + Tailwind + shadcn/ui | Akın'ın standart frontend stack'i |
| Database | Neon PostgreSQL + pgvector | Free tier, semantic search için |
| AI (Primary) | Groq Llama 3.3 70B | Hızlı, ücretsiz, yeterli kalite |
| AI (Fallback) | Google Gemini 2.0 Flash | Groq down olduğunda |
| Email Send | Gmail SMTP (Faz 1) → Resend (Faz 2) | Hızlı başlangıç, kademeli professionalleşme |
| Email Parse | Spring Mail | Bounce, reply detection |
| Web Scraping | Cheerio (N8N) + Jsoup (Spring Boot) | Şirket sitesi analizi |
| Discovery | Google Maps API + Crunchbase free | Şirket bulma |
| Notification | Slack Webhook | Onay bekleyen email'ler |
| Auth (Web) | NextAuth.js v5 | Akın'ın standart auth çözümü |
| Auth (API) | Spring Security + JWT | Service-to-service |
| Build | Maven (api), pnpm (web) | Standart |
| Deploy | Render (api + n8n), Vercel (web), Neon (db) | Tüm free tier |

## AI Kullanımı — 2 Nokta

Sistem **2 noktada** AI kullanır:

**1. Analyzer Agent**
- Şirket sitesinin içeriğini analiz eder
- Çıktı: sektör, büyüklük, dil, problem alanları, ülke
- Model: Groq Llama 3.3 70B
- Token/çağrı: ~1500 input + 500 output

**2. Writer Agent**
- Her şirket için kişiselleştirilmiş email yazar
- 7 ürünü portfolio overview olarak sunar
- Şirketin profiline göre vurguyu ayarlar
- Model: Groq Llama 3.3 70B
- Token/çağrı: ~1500 input + 800 output

**Filter ve Matcher agent'ları YOK** (sadeleştirildi):
- Filter: B2B email outreach'te post filtreleme gibi bir gereksinim yok
- Matcher: Sabit portfolio overview email'i olduğu için ürün eşleştirme gerek yok

## Mimari Özet

```
┌────────────────────────────────────────────────────────────────┐
│                      N8N Orchestrator                            │
│  (Discovery scheduling, retry, webhook routing)                  │
└─┬──────────┬──────────┬──────────┬──────────┬──────────────────┘
  │          │          │          │          │
  ▼          ▼          ▼          ▼          ▼
Google   Sitemap   Spring     Spring    Gmail
Maps     Scrape    /analyze   /write    SMTP
API                (AI #1)    (AI #2)
  │          │          │          │          │
  └──────────┴──────────┴──────────┴──────────┘
                       │
              ┌────────▼─────────┐
              │ Neon PostgreSQL  │
              │  + pgvector      │
              └────────┬─────────┘
                       │
              ┌────────▼─────────┐
              │  Next.js         │
              │  Dashboard       │
              └──────────────────┘
```

**Detaylı mimari:** `docs/ARCHITECTURE.md`
**Veritabanı şeması:** `docs/DATABASE.md`
**Task listesi:** `docs/CHECKLIST.md`
**AI prompt'ları:** `docs/PROMPTS.md`

## Senior Development Prensipleri

**Kod kalitesi:**
- Java: SOLID, DRY, KISS, max ~200 satır/dosya
- TypeScript strict mode (no `any`)
- Zod validation (frontend), Jakarta Validation (backend)
- DTO pattern her endpoint'te
- MapStruct ile DTO ↔ Entity dönüşümü
- Conventional commits
- Build test her feature sonrası
- No TODO in production code

**Veri katmanı:**
- Flyway migrations (versiyonlanmış)
- Repository pattern (Spring Data JPA)
- Tüm zaman damgaları UTC, frontend'de localize
- Soft delete (deleted_at)

**API tasarımı:**
- REST, JSON, kebab-case URL
- Versioned: `/api/v1/...`
- Rate limiting (Bucket4j)
- OpenAPI/Swagger docs
- Idempotent POST'lar için `Idempotency-Key`

**Email-spesifik kurallar:**
- Suppression list ZORUNLU her gönderim öncesi
- Bounce handling: hard bounce → kalıcı kara liste
- Unsubscribe link her email'de zorunlu (CAN-SPAM)
- Sender adresi truthful olmalı
- Volume warming bypass edilemez (kod zorlar)

## Akın'ın Çalışma Stili

- **Türkçe** konuşuyoruz, kod İngilizce
- **Konuşarak öğreniyor** — her büyük karar tartışılarak alınıyor
- **Plan-first** — kod yazmadan önce dökümantasyon
- **Real project tied** — her öğretim Akın'ın gerçek projeleriyle bağlantılı
- **No vibecoding** — tasarım, test, sonra kod

## Sözlük

- **Company** — Sistemin keşfettiği şirket (Google Maps veya scraping ile bulunan)
- **Email Account** — Bir şirketin jenerik kurumsal email adresi (info@, kariyer@, vb.)
- **Email Draft** — AI Writer'ın oluşturduğu email taslağı
- **Approval** — Akın'ın "evet, gönder" dediği an
- **Send** — SMTP üzerinden gönderilen email
- **Bounce** — Geri gelen email (hard bounce kalıcı, soft bounce geçici)
- **Reply** — Şirketten gelen yanıt
- **Suppression List** — Bir daha mail atılmayacak adresler (bounce + unsubscribe + complaint)

## Bağlantılı Projeler

- **akin-portfolio-mcp** (npm) — ürün metadata kaynağı (opsiyonel, Faz 1'de hardcoded)
- **AI Chatbot Platform** — RAG pattern referansı (pgvector + HuggingFace)
- **LeadPilot** — multi-agent pipeline pattern referansı

## Yasal Disclaimers

- Bu sistem **avukat tarafından review edilmemiştir**
- Yasal görüş profesyonel KVKK uzmanından alınmalıdır
- Bu kod ve mimari, **dürüst niyet + en iyi pratik** ile inşa edilmiştir
- Üçüncü tarafların kullanımında her kullanıcının kendi yasal sorumluluğu vardır
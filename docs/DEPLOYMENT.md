# Production Deployment Guide — AI Email Outreach Engine

> **Akın için adım adım production deployment.** Sıra:
> **Secrets → Neon → Render API → (N8N) → Vercel → CORS → Smoke test → UptimeRobot**
>
> ⚠️ Env var isimleri bu rehberde **koddan doğrulanmıştır** (`apps/api/src/main/resources/application.yml`).
> Eski `GMAIL_USERNAME` / `GMAIL_APP_PASSWORD` / `GMAIL_SMTP_*` isimleri kod tarafından **okunmaz** — kullanma.
> Tahmini süre: **60–90 dk**.

---

## 0. Ön Koşullar

- [ ] GitHub repo: https://github.com/akincskn/AI-Email-Outreach-Engine (push edildi ✅)
- [ ] Render.com hesabı (free tier)
- [ ] Vercel hesabı (free tier)
- [ ] Neon.tech hesabı (free tier)
- [ ] UptimeRobot hesabı (opsiyonel — keep-alive)
- [ ] Gmail outreach hesabı: `akin.tools.outreach@gmail.com` + **App Password** hazır
- [ ] Groq + Gemini API key'leri hazır

---

## 1. Production Secrets Üret

`scripts/generate-prod-secrets.ps1` çalıştır (PowerShell):

```powershell
.\scripts\generate-prod-secrets.ps1
```

4 secret üretir: **API_KEY**, **HMAC_SECRET**, **JWT_SECRET**, **AUTH_SECRET**.
Hepsini bir password manager'a kaydet. Bunları aşağıda Render + Vercel'e gireceksin.

> `API_KEY` Render (backend) ile Vercel (frontend) arasında **AYNI** olmalı — backend bearer auth bununla doğrulanıyor.

---

## 2. Neon PostgreSQL

### 2.1 Proje
1. https://neon.tech → **New Project**
2. Name: `ai-email-outreach`, Region: **EU (Frankfurt)** (Render ile aynı bölge), Postgres **16**

### 2.2 Extensions
Neon SQL Editor:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

### 2.3 Connection string → JDBC'ye çevir ⚠️
Neon sana şunu verir:
```
postgresql://USER:PASSWORD@ep-xxxx.eu-central-1.aws.neon.tech/DBNAME?sslmode=require
```
Spring Boot prod profili `DATABASE_URL`'i **doğrudan** `spring.datasource.url`'e koyar → **JDBC formatı zorunlu**. Üç parçaya ayır:

| Render env var | Değer |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://ep-xxxx.eu-central-1.aws.neon.tech/DBNAME?sslmode=require` |
| `DATABASE_USERNAME` | `USER` |
| `DATABASE_PASSWORD` | `PASSWORD` |

> Yani: başına `jdbc:` ekle, `user:password@` kısmını URL'den **çıkar**, ayrı env var'lara koy. `?sslmode=require` kalmalı.

Flyway V1–V… migration'ları Spring ilk açılışta otomatik uygular (`ddl-auto: validate`).

---

## 3. Render — Spring Boot API

Repo'da `apps/api/Dockerfile` + kök `render.yaml` var. İki yol:

- **A (önerilen, manuel):** Web Service'i UI'den oluştur, env var'ları aşağıdaki tablodan elle gir.
- **B (Blueprint):** Render → New → Blueprint → repo seç → `render.yaml`'ı okur, `sync: false` olanları sorar.

### 3.1 Web Service (Yol A)
1. Render → **New +** → **Web Service** → GitHub repo'yu bağla
2. Ayarlar:

| Alan | Değer |
|---|---|
| Name | `outreach-api` |
| Region | **Frankfurt** |
| Branch | `master` |
| Runtime | **Docker** |
| Root Directory | `apps/api` |
| Dockerfile Path | `Dockerfile` |
| Health Check Path | `/actuator/health` |
| Instance Type | **Free** |

> Docker kullandığımız için Build/Start command **yok** — Dockerfile hallediyor. App `$PORT`'a bind olur (Render otomatik enjekte eder).

### 3.2 Environment Variables (koddan doğrulanmış)

| Key | Değer / Kaynak |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | §2.3 (JDBC formatı!) |
| `DATABASE_USERNAME` | §2.3 |
| `DATABASE_PASSWORD` | §2.3 |
| `MAIL_HOST` | `smtp.gmail.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | `akin.tools.outreach@gmail.com` |
| `MAIL_PASSWORD` | Gmail **App Password** (SMTP + IMAP ikisi de bunu kullanır) |
| `MAIL_FROM` | `akin.tools.outreach@gmail.com` (**zorunlu** — default `test@localhost` geçersiz) |
| `MAIL_FROM_NAME` | `Akın Coşkun` |
| `GMAIL_IMAP_HOST` | `imap.gmail.com` |
| `GMAIL_IMAP_PORT` | `993` |
| `GMAIL_ACCOUNT_CREATED_AT` | `2026-05-04` (warming cap hesabını sürer) |
| `GROQ_API_KEY` | https://console.groq.com |
| `GEMINI_API_KEY` | https://aistudio.google.com |
| `AI_MOCK_ENABLED` | `false` (**prod'da mutlaka false** — true sabit fixture döner) |
| `GOOGLE_MAPS_API_KEY` | GCP Maps (opsiyonel; OSM discovery default) |
| `SENDER_PHYSICAL_ADDRESS` | Gerçek posta adresi (CAN-SPAM — boşsa footer'da "Your Address Here" leak) |
| `SENDER_GITHUB` | `https://github.com/akincskn` |
| `SENDER_PORTFOLIO` | `https://akin-coskun.web.app` |
| `API_KEY` | §1 (Vercel ile **AYNI**) |
| `HMAC_SECRET` | §1 |
| `JWT_SECRET` | §1 |
| `N8N_WEBHOOK_API_KEY` | Rastgele güçlü string (N8N → API webhook auth) |
| `BASE_URL` | Service URL atandıktan sonra doldur (§3.4) |
| `CORS_ALLOWED_ORIGINS` | Vercel deploy sonrası doldur (§5) |
| `SLACK_WEBHOOK_URL` | Slack Incoming Webhook (opsiyonel) |
| `RATE_LIMIT_RPM` | `100` |

> **GİRME:** `LOCAL_TEST_RECIPIENT_OVERRIDE` — set edilirse tüm mailler tek adrese yönlenir, prod'da gerçek alıcıya gitmez. Boş bırak.
> `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD`, `GMAIL_SMTP_HOST`, `GMAIL_SMTP_PORT`, `SENDER_NAME` → kod okumaz, **girme**.

### 3.3 Deploy
**Create Web Service** → build log'u izle (Docker build ~5–8 dk). `Started OutreachApplication` görünce hazır.

### 3.4 BASE_URL'i güncelle
Render service URL'ini verir: `https://outreach-api-XXXX.onrender.com`.
→ Environment → `BASE_URL` = bu URL → **Save** (redeploy tetiklenir).
Unsubscribe/tracking linkleri bu URL'i kullanır; yanlışsa linkler localhost'a gider.

### 3.5 Health
```bash
curl https://outreach-api-XXXX.onrender.com/actuator/health
# → {"status":"UP"}
```

---

## 4. Render — N8N Orchestrator (opsiyonel, otomasyon için)

1. Render → **New +** → **Web Service** → Runtime **Existing Image** → `docker.io/n8nio/n8n:latest`, Free, Frankfurt
2. **Disk ekle** (zorunlu — yoksa her restart'ta workflow'lar silinir): Name `n8n-data`, Mount `/home/node/.n8n`, 1 GB
3. Env: `N8N_HOST=outreach-n8n.onrender.com`, `N8N_PROTOCOL=https`, `WEBHOOK_URL=https://outreach-n8n.onrender.com/`, `GENERIC_TIMEZONE=UTC`, `DB_TYPE=sqlite`, `N8N_BASIC_AUTH_ACTIVE=true`, `N8N_BASIC_AUTH_USER=<user>`, `N8N_BASIC_AUTH_PASSWORD=<pass>`, `SPRING_API_URL=https://outreach-api-XXXX.onrender.com`
4. `workflows/*.json` dosyalarını N8N UI'den import et → Active yap

---

## 5. Vercel — Next.js Dashboard

1. https://vercel.com → **Add New → Project** → repo'yu import et
2. Ayarlar:

| Alan | Değer |
|---|---|
| Framework Preset | **Next.js** (auto) |
| Root Directory | `apps/web` |
| Build Command | `pnpm build` (auto) |
| Install Command | `pnpm install` (auto) |
| Output Directory | `.next` (auto) |

3. Environment Variables:

| Key | Değer |
|---|---|
| `DASHBOARD_USERNAME` | `akin` |
| `DASHBOARD_PASSWORD` | Güçlü production şifresi |
| `AUTH_SECRET` | §1 (AUTH_SECRET) |
| `NEXTAUTH_SECRET` | `AUTH_SECRET` ile **AYNI** |
| `NEXTAUTH_URL` | `https://<vercel-domain>.vercel.app` |
| `NEXT_PUBLIC_API_URL` | `https://outreach-api-XXXX.onrender.com` |
| `API_KEY` | Render `API_KEY` ile **AYNI** (server-side proxy bearer) |

4. **Deploy** (~2 dk). URL: `https://outreach-XXX.vercel.app`

---

## 6. CORS'u Bağla

Render → `outreach-api` → Environment → `CORS_ALLOWED_ORIGINS` = `https://outreach-XXX.vercel.app` → Save (redeploy).

---

## 7. Smoke Test

```bash
API=https://outreach-api-XXXX.onrender.com

# 7.1 Health
curl $API/actuator/health                       # → {"status":"UP"}

# 7.2 Auth gate
curl -i $API/api/v1/companies                    # → 401 Unauthorized
curl -H "Authorization: Bearer <API_KEY>" $API/api/v1/companies   # → 200 OK
```

**7.3 Dashboard login:** `https://outreach-XXX.vercel.app` → `akin` / production şifresi → dashboard kartları backend'den dolmalı (Volume Today, Pending Approvals).

**7.4 End-to-end (dikkat — prod gerçek alıcıya gönderir):**
İlk test'i **gerçek bir şirkete gönderme.** Sahte bir company seed et (örn. `info@nonexistent-test-XYZ.invalid`), pipeline'ı çalıştır, draft oluşsun, dashboard'da **Approve** et, gönderim akışının hatasız ilerlediğini doğrula. Gerçek outreach'e ancak warming penceresi (hesap +14 gün) dolduktan sonra geç.

> ⚠️ `LOCAL_TEST_RECIPIENT_OVERRIDE` prod'da **set değil** — yani onayladığın her mail gerçek alıcıya gider.

---

## 8. UptimeRobot (opsiyonel — keep-alive)

Render free tier ~15 dk inaktiflikte uyur (ilk istek yavaş). Önlemek için:
1. https://uptimerobot.com → **New Monitor** → Type **HTTP(s)**
2. URL: `https://outreach-api-XXXX.onrender.com/actuator/health`
3. Interval: **5 dk** (free tier limiti)

---

## 9. Troubleshooting

### Render build fail
- Build log'da hangi step? Docker layer cache sorunu → "Clear build cache & deploy".
- Maven `dependency:go-offline` timeout → tekrar deploy (Render bazen ağ yavaşlar).

### App açılıyor ama /actuator/health 503 / DOWN
- **DB bağlantısı:** `DATABASE_URL` JDBC formatında mı (`jdbc:postgresql://...`)? Neon ham `postgresql://` verirse Spring patlar (§2.3).
- `DATABASE_USERNAME` / `DATABASE_PASSWORD` set mi? `sslmode=require` var mı?
- **Flyway validate hatası:** Neon'da şema drift → Neon SQL Editor'den logları kontrol et.

### Mail gönderilmiyor / reply okunmuyor
- `MAIL_USERNAME` / `MAIL_PASSWORD` set mi? (IMAP de bunları kullanır.) App Password güncel mi (boşluksuz 16 char)?
- `MAIL_FROM` set mi? Default `test@localhost` reddedilir.
- **Hiç mail çıkmıyor:** `GMAIL_ACCOUNT_CREATED_AT` + 14 gün warming penceresi dolmamış olabilir → VolumeLimiterService cap=0 döndürür (tasarım gereği).
- Gmail SMTP limiti: günde ~500.

### Vercel build/login fail
- `AUTH_SECRET` (ve alias `NEXTAUTH_SECRET`) eksik → NextAuth patlar.
- Login sonrası backend 401 → Vercel `API_KEY` ile Render `API_KEY` **aynı değil**.
- Dashboard kartları boş/0 → `NEXT_PUBLIC_API_URL` yanlış veya backend uyuyor (ilk istek ~30 sn).

### CORS hatası (tarayıcı console)
- `CORS_ALLOWED_ORIGINS` Vercel domain'iyle birebir eşleşmeli (https, trailing slash yok).

---

## Sıra Özeti

```
1. generate-prod-secrets.ps1  → API_KEY, HMAC_SECRET, JWT_SECRET, AUTH_SECRET
2. Neon                       → DB + extensions + JDBC connection string
3. Render API (Docker)        → env vars → deploy → BASE_URL güncelle → /health UP
4. (N8N — opsiyonel)
5. Vercel                     → env vars (API_KEY aynı!) → deploy
6. CORS_ALLOWED_ORIGINS       → Vercel URL → redeploy
7. Smoke test                 → health / 401-200 / login / e2e (sahte alıcı)
8. UptimeRobot                → keep-alive
```

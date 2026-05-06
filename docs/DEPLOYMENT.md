# Deployment Guide — AI Email Outreach Engine

> **Bu rehber Akın için adım adım deployment talimatıdır.**
> Aşağıdaki sırayı takip et: Neon → Render API → Render N8N → Vercel.

---

## Ön Koşullar

- [ ] GitHub repo: `ai-email-outreach` (private)
- [ ] Render.com hesabı (free tier yeterli)
- [ ] Vercel hesabı (free tier yeterli)
- [ ] Neon.tech hesabı (free tier yeterli)
- [ ] `.env.example`'daki tüm key'ler hazır

---

## 1. Neon PostgreSQL Kurulumu

### 1.1 Proje Oluştur
1. https://neon.tech → **New Project**
2. Name: `ai-email-outreach`
3. Region: **EU Frankfurt** (Render ile aynı bölge — düşük latency)
4. Postgres version: **16**
5. **Create project** tıkla

### 1.2 Extensions Aktive Et
Neon console → SQL Editor:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

### 1.3 Connection String Al
- Dashboard → Connection string → **Connection string** sekmesi
- Format: `postgresql://user:password@endpoint/db?sslmode=require`
- Bu değeri `DATABASE_URL` env var'ına yapıştır

### 1.4 Migrations
Migrations Flyway tarafından otomatik uygulanır. Spring Boot ilk başladığında V1–V14 çalışır.

---

## 2. Render — Spring Boot API

### 2.1 Web Service Oluştur
1. https://render.com → **New +** → **Web Service**
2. **Connect a repository** → GitHub → `ai-email-outreach`
3. Ayarlar:

| Alan | Değer |
|---|---|
| Name | `outreach-api` |
| Region | Frankfurt (EU Central) |
| Branch | `main` |
| Runtime | **Docker** |
| Root Directory | `apps/api` |
| Dockerfile Path | `Dockerfile` |
| Instance Type | **Free** |

### 2.2 Environment Variables

Aşağıdaki değerleri Render Dashboard → Environment → Add:

| Key | Değer / Kaynak |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | Neon connection string (§1.3) |
| `GROQ_API_KEY` | https://console.groq.com |
| `GEMINI_API_KEY` | https://aistudio.google.com |
| `GOOGLE_MAPS_API_KEY` | GCP Console → Maps Platform |
| `GMAIL_USERNAME` | `akin.tools.outreach@gmail.com` |
| `GMAIL_APP_PASSWORD` | Gmail → Settings → Security → App passwords |
| `GMAIL_SMTP_HOST` | `smtp.gmail.com` |
| `GMAIL_SMTP_PORT` | `587` |
| `GMAIL_IMAP_HOST` | `imap.gmail.com` |
| `GMAIL_IMAP_PORT` | `993` |
| `GMAIL_ACCOUNT_CREATED_AT` | `2026-05-04` |
| `SLACK_WEBHOOK_URL` | Slack → App → Incoming Webhooks |
| `HMAC_SECRET` | `openssl rand -hex 32` ile üret |
| `JWT_SECRET` | `openssl rand -hex 32` ile üret |
| `N8N_WEBHOOK_API_KEY` | Rastgele güvenli string |
| `SENDER_PHYSICAL_ADDRESS` | Gerçek posta adresi (CAN-SPAM zorunlu) |
| `CORS_ALLOWED_ORIGINS` | `https://YOUR_VERCEL_URL.vercel.app` |
| `RATE_LIMIT_RPM` | `100` |

### 2.3 Health Check
- Health Check Path: `/actuator/health`
- Render bu endpoint'i kullanarak service'in hazır olup olmadığını anlar

### 2.4 Deploy
- **Create Web Service** → Build log'u izle (~5 dk)
- Log'da `Started OutreachApplication` görünce hazır
- Test: `https://outreach-api.onrender.com/actuator/health` → `{"status":"UP"}`

---

## 3. Render — N8N Orchestrator

### 3.1 Web Service Oluştur
1. Render → **New +** → **Web Service**
2. Ayarlar:

| Alan | Değer |
|---|---|
| Name | `outreach-n8n` |
| Region | Frankfurt (EU Central) |
| Runtime | **Existing Image** |
| Image URL | `docker.io/n8nio/n8n:latest` |
| Instance Type | **Free** |

### 3.2 Persistent Disk Ekle
- **Disks** sekmesi → **Add Disk**
- Name: `n8n-data`
- Mount Path: `/home/node/.n8n`
- Size: **1 GB**

> ⚠️ Disk olmadan N8N her restart'ta sıfırlanır (workflow'lar kaybolur)

### 3.3 Environment Variables

| Key | Değer |
|---|---|
| `N8N_HOST` | `outreach-n8n.onrender.com` |
| `N8N_PROTOCOL` | `https` |
| `WEBHOOK_URL` | `https://outreach-n8n.onrender.com/` |
| `GENERIC_TIMEZONE` | `UTC` |
| `DB_TYPE` | `sqlite` |
| `N8N_BASIC_AUTH_ACTIVE` | `true` |
| `N8N_BASIC_AUTH_USER` | Güvenli bir username |
| `N8N_BASIC_AUTH_PASSWORD` | Güvenli bir şifre |
| `SPRING_API_URL` | `https://outreach-api.onrender.com` |

### 3.4 N8N Workflow Import
1. `https://outreach-n8n.onrender.com` → Login
2. **Settings** → **Import from file**
3. `workflows/company-discovery.json` dosyasını import et
4. Workflow ayarları → **Active** yap

---

## 4. Vercel — Next.js Dashboard

### 4.1 Proje Oluştur
1. https://vercel.com → **New Project**
2. **Import Git Repository** → `ai-email-outreach`
3. Ayarlar:

| Alan | Değer |
|---|---|
| Framework Preset | **Next.js** |
| Root Directory | `apps/web` |
| Build Command | `pnpm build` (veya `npm run build`) |
| Output Directory | `.next` (default) |

### 4.2 Environment Variables

| Key | Değer |
|---|---|
| `AUTH_SECRET` | `openssl rand -base64 32` ile üret |
| `AUTH_URL` | `https://YOUR_VERCEL_APP.vercel.app` |
| `DASHBOARD_USERNAME` | `akin` |
| `DASHBOARD_PASSWORD` | Güvenli şifre |
| `NEXT_PUBLIC_API_URL` | `https://outreach-api.onrender.com` |

### 4.3 Deploy
- **Deploy** tıkla → ~2 dk
- `https://YOUR_APP.vercel.app/login` → `akin` / şifre ile giriş yap

### 4.4 CORS Güncelle
Deploy bittikten sonra Vercel URL'ini Render API'ye ekle:
- Render → `outreach-api` → Environment → `CORS_ALLOWED_ORIGINS` = `https://YOUR_APP.vercel.app`
- **Manual Deploy** tetikle

---

## 5. Custom Domain (Opsiyonel)

### Vercel
1. Vercel → Project → **Domains** → `outreach.akincoskun.dev`
2. DNS provider'ında CNAME: `outreach` → `cname.vercel-dns.com`

### Render API
1. Render → `outreach-api` → **Custom Domain** → `api.akincoskun.dev`
2. DNS: CNAME `api` → render domain

SSL her iki platform için de **otomatik** (Let's Encrypt).

---

## 6. Deployment Sonrası Kontrol

```bash
# API health
curl https://outreach-api.onrender.com/actuator/health

# Swagger UI
open https://outreach-api.onrender.com/swagger-ui.html

# Dashboard login
open https://YOUR_APP.vercel.app/login

# N8N UI
open https://outreach-n8n.onrender.com
```

---

## ⚠️ Önemli Notlar

- **Render free tier** uyku moduna girer (inaktif ~15 dk sonra). İlk istek yavaş olabilir.
- **Gmail SMTP:** Hesap warmup bitmeden (`GMAIL_ACCOUNT_CREATED_AT` + 14 gün) hiç mail çıkmaz (VolumeLimiterService cap=0 döndürür).
- **N8N disk:** Disk olmadan tüm workflow'lar kaybolur — her zaman disk ekle.
- **Neon free tier:** 0.5 GB storage, 1 compute unit. Yeterli Faz 1 için.

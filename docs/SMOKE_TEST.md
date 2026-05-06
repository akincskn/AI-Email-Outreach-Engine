# Smoke Test Runbook — AI Email Outreach Engine

> **Ne zaman çalıştırılır:** Render + Vercel deploy başarılı olduktan sonra,
> Gmail hesabı en az 14 gün eski olduğunda (2026-05-04 açıldı → 18 Mayıs'tan sonra).

---

## Pre-conditions Checklist

Aşağıdakilerin HEPSİ doğru olmalı, yoksa başlama:

- [ ] `GMAIL_ACCOUNT_CREATED_AT=2026-05-04` → bugün 18 Mayıs veya sonrası (14 gün doldu)
- [ ] `https://outreach-api.onrender.com/actuator/health` → `{"status":"UP"}`
- [ ] Vercel dashboard `/login` → `akin` / şifre ile giriş yapılabiliyor
- [ ] N8N UI erişilebilir (`https://outreach-n8n.onrender.com`)
- [ ] N8N'de `company-discovery` workflow aktif
- [ ] Neon DB'de Flyway migrasyonları geçmiş (V1–V14)
- [ ] `SENDER_PHYSICAL_ADDRESS` gerçek bir adres ile dolu
- [ ] `GROQ_API_KEY` ve `GEMINI_API_KEY` set edilmiş

---

## Test Sırası

### ADIM 1 — Veritabanı Temiz Başlangıç Kontrolü

Neon SQL Editor'da:
```sql
SELECT COUNT(*) FROM companies;           -- 0 olmalı (veya seed varsa 6)
SELECT COUNT(*) FROM email_drafts;        -- 0 olmalı
SELECT COUNT(*) FROM suppression_list;    -- 0 olmalı
```

---

### ADIM 2 — Discovery Filter Seç

Dashboard → `/settings` → Discovery Filters bölümünde mevcut filtreler:
- Istanbul Restaurants → TR, küçük hacim → **bu filtreyi kullan**

> Büyük şehir/geniş sektör seçme. İlk testte 5-10 şirket yeterli.

---

### ADIM 3 — N8N Manuel Tetikle

1. N8N → `company-discovery` workflow → **Execute Workflow**
2. Workflow her node'da yeşil mi? Log'u izle.
3. `Get Active Filters` → veri döndürdü mü?
4. `Trigger Discovery per Filter` → HTTP 200 mi?
5. `Extract Emails` → `emailsFound > 0` mu?
6. `Analyze Company` → `status: ANALYZED` mi?
7. `Is Blacklisted?` → false branch (yeşil) → `Get Email Accounts` çalıştı mı?
8. `Write Email Draft` → HTTP 200, `status: PENDING` mi?

---

### ADIM 4 — Veritabanı Snapshot

```sql
SELECT COUNT(*), status FROM companies GROUP BY status;
-- Beklenti: 5-10 ANALYZED, belki bazı BLACKLISTED

SELECT COUNT(*) FROM email_accounts;
-- Beklenti: her company için 1-3 email address

SELECT COUNT(*), status FROM email_drafts GROUP BY status;
-- Beklenti: PENDING (onay bekleyen) draft'lar var
```

---

### ADIM 5 — Dashboard'da Draft Kontrol

1. `https://YOUR_APP.vercel.app/drafts` → draft listesi görünüyor mu?
2. Bir draft'ı aç → body render oluyor mu?
3. Body'de `PLACEHOLDER` görmek **NORMAL** (henüz gönderilmedi)
4. `{{PHYSICAL_ADDRESS}}` veya `{{UNSUBSCRIBE_URL}}` placeholder'ı **görünmemeli** (bunlar zaten çözüldü)

---

### ADIM 6 — İlk Mail Akın'a (Kendi Alternatif Gmail)

> ⚠️ İlk gerçek mail **dışarıya değil, Akın'ın kendi alternatif Gmail'ine** atılmalı.

1. Bir draft seç → **Edit** → `To` adresini kendi alternatif Gmail'inle değiştir
2. Subject ve body'yi gözden geçir, gerekirse düzenle
3. **Approve & Send** tıkla
4. `email_sends` tablosunda `status = SENT` mi?

```sql
SELECT id, to_email, status, sent_at FROM email_sends ORDER BY created_at DESC LIMIT 5;
```

---

### ADIM 7 — Gmail Gelen Kutusu Kontrol

Alternatif Gmail hesabında:
- [ ] Mail geldi mi?
- [ ] Subject doğru mu?
- [ ] Gönderen: `akin.tools.outreach@gmail.com`
- [ ] **Body'de PLACEHOLDER YOK mu?** (`/unsubscribe?token=PLACEHOLDER` görmemeli)
- [ ] Gerçek unsubscribe linki var mı? (HMAC token formatında)
- [ ] Tracking pixel var mı? (`<img>` HTML'de)
- [ ] Fiziksel adres footer'da var mı?

---

### ADIM 8 — mail-tester.com Spam Skoru

1. https://mail-tester.com → unique test adresi al (örn: `test-xyz@mail-tester.com`)
2. Bu adrese bir draft approve et ve gönder
3. mail-tester.com'a geri dön → skoru gör
4. **Hedef: 8/10 veya üzeri**

Skor düşükse kontrol et:
- SPF record yok → Gmail otomatik ekler, ama custom domain için gerekli
- Spam kelimeler body'de
- Unsubscribe link eksik (şimdi var)

---

### ADIM 9 — Tracking Pixel Test

1. Adım 7'deki email'i Gmail'de **aç** (sadece aç — HTML görüntülensin)
2. Birkaç saniye bekle
3. Kontrol et:

```sql
SELECT * FROM email_opens WHERE send_id = 'YOUR_SEND_ID';
-- 1 kayıt olmalı (first-open-only garantili)
```

Alternatif olarak: email HTML'inden `src` URL'ini kopyala, browser'da aç → GIF döndü mü?

---

### ADIM 10 — Unsubscribe Test

1. Adım 7'deki email'deki unsubscribe linkine tıkla
2. `"Mailing list'ten çıkarıldınız"` sayfası geliyor mu?
3. Kontrol et:

```sql
SELECT * FROM suppression_list WHERE email = 'YOUR_TEST_EMAIL';
-- 1 kayıt olmalı, reason = 'unsubscribe'
```

---

### ADIM 11 — Suppression Koruması Test

1. Adım 10'dan sonra aynı email adresiyle yeni draft oluştur ve approve et
2. Gönderimi dene
3. Kontrol et:

```sql
SELECT status FROM email_sends WHERE to_email = 'YOUR_TEST_EMAIL' ORDER BY created_at DESC LIMIT 1;
-- status = 'SUPPRESSED' olmalı, MailHog/Gmail'e düşmemeli
```

---

### ADIM 12 — Reply Tracking Test

1. Alternatif Gmail'den gelen maile **Reply** yap (kısa bir metin)
2. 5-6 dakika bekle (ReplyTracker 5 dk'da bir poll yapar)
3. Slack kanalında bildirim geldi mi? `"Reply from ... "` formatında
4. Kontrol et:

```sql
SELECT * FROM email_replies ORDER BY received_at DESC LIMIT 5;
-- reply kayıtlanmış olmalı
```

---

### ADIM 13 — Bounce Tracking Test

1. Kasıtlı geçersiz adrese mail gönder: `thisaddressdoesnotexist12345@gmail.com`
2. 5-6 dakika bekle
3. Kontrol et:

```sql
SELECT * FROM email_bounces ORDER BY detected_at DESC LIMIT 5;
-- bounce_type = 'hard' olmalı

SELECT * FROM suppression_list WHERE email = 'thisaddressdoesnotexist12345@gmail.com';
-- suppression_list'e eklenmeli
```

---

### ADIM 14 — Volume Limiter Test

Hesap 14-27 gün arası ise daily cap = **5**.

```sql
SELECT * FROM volume_log WHERE sent_date = CURRENT_DATE;
-- sent_count / daily_cap göreceksin
```

5 mail gönderildikten sonra 6. gönderimde:
```
Daily volume cap reached (5/5)
```
hatası beklenebilir.

---

## Geçer Kriterleri

| Test | Beklenti |
|---|---|
| companies keşfedildi | ≥5 ANALYZED |
| draft oluştu | ≥1 PENDING draft |
| Mail alındı | ✅ (kendi alternatif Gmail) |
| **Body'de PLACEHOLDER YOK** | ✅ Kritik |
| mail-tester.com skoru | ≥8/10 |
| Tracking pixel çalışıyor | email_opens kaydı var |
| Unsubscribe çalışıyor | suppression kaydı var |
| Suppression koruması | 2. send SUPPRESSED |
| Reply tracking | email_replies kaydı var |
| Bounce tracking | email_bounces kaydı var |
| Volume limiter | cap'i geçince hata |

---

## Hata Durumunda

| Belirti | Kontrol |
|---|---|
| Draft body'de PLACEHOLDER | EmailSendOrchestrator replace çalışmıyor |
| mail-tester < 8/10 | Spam words, eksik header, SPF |
| Reply yakalanmıyor | IMAP credential yanlış, 5 dk bekle |
| Bounce yakalanmıyor | BounceTrackerService log'unu kontrol |
| Volume cap 0 | GMAIL_ACCOUNT_CREATED_AT yanlış veya <14 gün |

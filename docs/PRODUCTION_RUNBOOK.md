# Production Runbook — AI Email Outreach Engine

> **Sistem canlıda.** Bu döküman Akın'ın günlük/haftalık operasyon checklist'i.

---

## Sabah Rutini (5 dk)

Her sabah aşağıdakileri kontrol et:

```
□ Slack #email-outreach kanalını aç
□ Yeni draft bildirimi var mı? → Dashboard'da onayla veya reddet
□ Reply bildirimi var mı? → /replies sayfasından yanıtla
□ Bounce alert var mı? → /sent filtresinden incele
□ Hata bildirimi var mı? (🚨 SMTP error, ⚠️ bounce rate)
```

---

## Dashboard Günlük Akış

### 1. Pending Drafts (/drafts)
- Yeni gelen draft'ları incele
- Şirket bilgisi sol panelde → relevance kontrol
- Email body sağ panelde → gerekirse düzenle
- **Approve & Send** veya **Reject**

> **Kural:** İlk 2 haftada her draft'ı elle oku, körü körüne onaylama.

### 2. Recent Replies (/replies)
- Gelen yanıtları incele
- Olumlu yanıt → ilgi var, ürün demosu teklif et
- Olumsuz/opt-out → zaten suppression'a eklendi (unsubscribe link kullandıysa)
- `Mark as Handled` ile işaretle

### 3. Volume Gauge (/ ana sayfa)
- Bugün X/Y gönderildi
- Cap'e yaklaşıyorsa ek onay verme

---

## Haftalık Kontrol (~15 dk)

### Bounce Rate İzle
```sql
SELECT 
  COUNT(*) FILTER (WHERE b.id IS NOT NULL) AS bounced,
  COUNT(*) AS total,
  ROUND(100.0 * COUNT(*) FILTER (WHERE b.id IS NOT NULL) / NULLIF(COUNT(*), 0), 2) AS bounce_pct
FROM email_sends s
LEFT JOIN email_bounces b ON b.send_id = s.id
WHERE s.sent_at >= NOW() - INTERVAL '7 days';
```

**⚠️ Bounce rate >2% → Dur, analiz yap**
**🚨 Bounce rate >5% → Kampanyayı durdur, SMTP kara listeye girebilir**

### AI Cost Analizi
```sql
SELECT 
  agent_name, 
  COUNT(*) AS calls,
  SUM(CASE WHEN success THEN 1 ELSE 0 END) AS successes,
  AVG(duration_ms) AS avg_ms
FROM ai_calls
WHERE created_at >= NOW() - INTERVAL '7 days'
GROUP BY agent_name;
```

### Suppression List Export (opsiyonel)
```sql
SELECT email, reason, created_at 
FROM suppression_list 
ORDER BY created_at DESC;
```

---

## Aylık Kontrol

### Warming Progression
```sql
SELECT sent_date, sent_count, daily_cap 
FROM volume_log 
ORDER BY sent_date DESC 
LIMIT 30;
```

Kap artışları (`GMAIL_ACCOUNT_CREATED_AT` baz alınarak otomatik):
- 0-2 hafta: 0/gün (warm up)
- 2-4 hafta: 5/gün
- 4-6 hafta: 10/gün
- 6-8 hafta: 20/gün
- 8+ hafta: 50/gün

### Pipeline Performance
```sql
SELECT 
  DATE_TRUNC('week', discovered_at) AS week,
  COUNT(*) AS discovered,
  COUNT(*) FILTER (WHERE status = 'ANALYZED') AS analyzed,
  COUNT(*) FILTER (WHERE status = 'BLACKLISTED') AS blacklisted
FROM companies
GROUP BY 1
ORDER BY 1 DESC;
```

---

## Acil Durum Prosedürleri

### Yüksek Bounce Rate
1. Yeni gönderimleri durdur (Dashboard → approve etme)
2. Bounce eden domainleri incele → pattern var mı?
3. MailTester skoru kontrol et (https://mail-tester.com)
4. SPF/DKIM sorun olabilir → Gmail ayarları kontrol

### SMTP Hatası
1. Render logs → `outreach-api` → `Failed to send to`
2. Gmail App Password expire olmuş olabilir → yeni al
3. Gmail account suspicious activity flag → Gmail'den confirm et

### N8N Workflow Durdu
1. N8N UI → Execution history → hata mesajı
2. Spring Boot API unreachable → Render service uyku modunda olabilir
3. `SPRING_API_URL` env var doğru mu?

### Suppression List Yanlış Kayıt
```sql
-- Manuel unsuppress (çok nadir, sadece hata durumunda)
DELETE FROM suppression_list WHERE email = 'email@example.com' AND reason = 'unsubscribe';
```

---

## Faz 2'ye Geçiş Sinyalleri

Aşağıdakiler gerçekleşince Faz 2 planlamaya başla:

- [ ] 30+ email başarıyla gönderildi
- [ ] Bounce rate stabil (<%2)
- [ ] En az 1 gerçek yanıt alındı
- [ ] Gmail hesabı 8+ haftalık → cap 50/gün
- [ ] Sistem 2+ hafta sorunsuz çalıştı

**Faz 2 gündemde:** Custom domain, Resend migration, reply auto-classifier, A/B test.

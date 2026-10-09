# [ADR-018] Yenileme token'ı: opak, hash'li, tek kullanımlık; tekrar kullanımda aile iptali

**Tarih:** 2026-10-07
**Durum:** Accepted · tolerans penceresi kararı [ADR-069](069-refresh-token-reuse-grace-window.md) ile değişti (2026-10-09)
**Kaynak:** `add-account-and-session` design.md D1

---

## Context

[ADR-008](008-jwt-auth-with-opaque-refresh.md) erişim token'ını kısa ömürlü bir JWT, yenileme token'ını ise "opak ve iptal edilebilir" olarak belirledi. Uygulamaya geçerken üç soru açıktı: token nasıl saklanır, ele geçirilmiş bir token nasıl fark edilir ve ömrü ne kadardır.

Yenileme token'ı ele geçirildiğinde saldırganın elinde 15 dakikalık değil haftalarca geçerli bir oturum olur. Rotasyon olmazsa bu token ömrü boyunca kullanılabilir. Rotasyon olup aile iptali olmazsa saldırgan ile gerçek kullanıcı aynı oturumu sessizce paylaşır.

## Decision

- Yenileme token'ı **256 bit rastgele** bir dizedir, JWT değildir. Veritabanında yalnızca **SHA-256 hash'i** saklanır; düz değer istemciye bir kez verilir.
- Her girişte yeni bir **aile** (`family_id`) başlar. Her yenilemede sunulan token `used_at` ile tüketilir ve yerine **aynı ailede** yenisi verilir (rotasyon).
- Zaten kullanılmış bir token tekrar sunulursa bu bir sızıntı işaretidir: **ailenin tamamı iptal edilir** (`revoked_at`). İptal, isteğin hatayla dönmesine rağmen kalıcıdır.
- Çıkış ailenin tamamını iptal eder ve bilinmeyen token için de `204` döner.
- Ömür **30 gündür**. Her rotasyon yeni bir 30 günlük ömür verir. Erişim token'ı ADR-008'deki gibi 15 dakikadır.
- Eşzamanlı iki yenileme aynı satırı göremez: arama `SELECT … FOR UPDATE` ile kilitlenir.

| Alternatif | Neden reddedildi |
|---|---|
| Yenileme token'ını da JWT yapmak | İptal edilemez; ADR-008 bunu zaten reddetti. |
| Rotasyonsuz opak token | Tek kullanımlıklık yok; sızan token süresiz kullanılabilir ve sızıntı fark edilemez. |
| Token'ı BCrypt ile hash'lemek | 256 bit entropili değer sözlük saldırısına açık değil; yavaş hash yalnızca CPU yakar ve eşitlik aramasını imkânsız kılar ([ADR-020](020-verification-tokens-sha256-single-use.md)). |
| Tekrar kullanımda yalnızca o token'ı reddetmek | Saldırgan zinciri devralmışsa meşru kullanıcının yeni token'ı da saldırganın elindedir; ailenin tamamını iptal etmek tek güvenli tepkidir. |
| Kısa tolerans penceresi (aynı token birkaç saniye içinde iki kez kabul) | Gerçek bir yarışı örter ama tespit gücünü zayıflatır; tek yenileme noktası olan BFF ile ihtiyaç kalmadı ([ADR-023](023-web-bff-cookies-and-header-csrf.md)). |

## Consequences

### Olumlu
- Çıkış gerçekten çıkıştır: yenileme yolu kapanır, erişim penceresi en fazla 15 dakikadır.
- Ele geçirilmiş bir token'ın ilk tekrar kullanımı tüm zinciri kapatır; saldırganla kullanıcı yan yana oturum süremez.
- Veritabanını okuyan biri oturum devralamaz.

### Olumsuz
- Aynı token'la gelen meşru iki eşzamanlı yenileme (ör. iki sekmede aynı anda süresi dolan sayfa) zinciri iptal eder ve kullanıcıyı çıkış yapmış sayar. `web` yenilemeyi tek gezintiye indirerek bunu önler; başka bir istemci eklenirse aynı disiplin gerekir.
- Her yenileme bir veritabanı yazması demektir (seyrek çağrılan bir yol, kabul edilebilir).

### Nötr / Trade-off
- Süresi dolmuş ve iptal edilmiş satırlar birikir; temizleme işi bugün yok. Hacim sorun olursa ayrı karar.

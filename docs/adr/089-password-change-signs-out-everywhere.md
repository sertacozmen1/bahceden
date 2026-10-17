# [ADR-089] Parola değiştirme, isteği yapan dahil bütün oturumları kapatır; oturum açmaz; e-postayı doğrulamaz

**Tarih:** 2026-10-17
**Durum:** Accepted
**Kaynak:** `add-password-change` design.md D3

---

## Context

Parola değiştirmenin en sık sebebi sızıntı şüphesidir; o anda hangi oturumun güvenilir olduğu bilinemez. Sıfırlama akışı bütün oturumları kapatır ve oturum açmaz ([ADR-035](035-password-reset-signs-out-everywhere-no-auto-login.md)).

Proje sahibi 2026-10-08'de seçimi yaptı: oturum açıkken parola değiştirildiğinde **mevcut oturum dahil** bütün oturumlar kapanır.

Sıfırlama e-postayı doğrulanmış işaretler, çünkü bağlantıyı kullanan gelen kutusuna erişmiştir ([ADR-036](036-password-reset-marks-email-verified.md)).

## Decision

Başarılı parola değişikliği tek bir transaction içinde:

1. Parola hash'ini değiştirir.
2. Hesabın **bütün** yenileme token ailelerini iptal eder, isteği yapan oturumunkini de.
3. Hesabın açık giriş kodlarını kapatır.

Ardından hesabın dilinde mevcut "parolanız değişti" bildirimi gönderilir; bildirim sıfırlama adresini içerir. Gönderim başarısız olursa değişiklik yine başarılıdır, hata yalnızca loglanır ([ADR-019](019-email-via-smtp-provider-agnostic.md)).

Yanıt yeni token **içermez**; kullanıcı olağan girişten geçer. `email_verified` **değişmez**.

| Alternatif | Neden reddedildi |
|---|---|
| Mevcut oturumu açık tutup diğerlerini kapatmak | Proje sahibi reddetti; oturumun kendisi çalınmışsa saldırgan içeride kalır. Ayrıca `web`'in yenileme token'ını da göndermesini gerektirirdi. |
| Değişiklikten sonra yeni token çifti dönmek | `POST /sessions` dışında ikinci bir kimlik yolu açar ve ikinci faktörü atlar (`AGENTS.md` §15). |
| Oturumlara dokunmamak | Sızıntı şüphesinde parolayı değiştirmenin anlamını yok eder. |
| `email_verified` işaretlemek | Parolayı bilmek gelen kutusuna erişimi kanıtlamaz; gerçeğe dayanmayan bir bilgi üretir. |

## Consequences

### Olumlu
- Parolanın değiştiği iki yol (sıfırlama ve oturumlu değiştirme) oturumlar için aynı sonucu verir; tek kural bilinir.
- Sızıntı şüphesinde bütün cihazlar tek adımda dışarıda kalır.

### Olumsuz
- Kullanıcı bütün cihazlarında, değişikliği yaptığı cihaz dahil, yeniden giriş yapar.
- Erişim token'ları en fazla 15 dakika daha geçerlidir ([ADR-008](008-jwt-auth-with-opaque-refresh.md)); yalnızca yenilenemezler.

### Nötr / Trade-off
- Sıfırlamayla tek farkı e-posta doğrulamasıdır; bu fark bilinçlidir.

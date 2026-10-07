# [ADR-035] Parola sıfırlama bütün oturumları kapatır; otomatik giriş yok

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-password-reset` design.md D3

---

## Context

Parola sıfırlamanın en sık sebebi unutmaktır. İkinci sebep, parolanın ele geçirildiği şüphesidir. İkinci durumda yalnızca parolayı değiştirmek yetmez: eski parolayla açılmış oturumlar ([ADR-018](018-refresh-token-rotation-with-family-revocation.md)) ve eski parolayla elde edilmiş ikinci faktör girişimleri ([ADR-025](025-second-factor-code-bound-to-account.md)) geçerli kalır.

`AGENTS.md` §15 iki kimlik yolu tutmayı yasaklar. Giriş bugün tek yoldan, `POST /sessions` ve gerekiyorsa `POST /sessions/challenge` üzerinden yapılıyor.

## Decision

`POST /api/v1/accounts/password-resets/confirmations` tek bir transaction içinde:

1. Parola hash'ini değiştirir (kurallar kayıttakiyle aynı: 10–200 karakter).
2. Token'ı tüketir.
3. Hesabın **bütün** yenileme token ailelerini iptal eder.
4. Hesabın açık giriş kodlarını kapatır.

Ardından hesabın dilinde **"parolanız değişti"** bildirimi gönderilir; bildirimde yeni sıfırlama bağlantısı istenecek adres de yer alır.

Yanıt **204**'tür ve **oturum açmaz**. `web` kullanıcıyı `/login?reset=1` adresine yönlendirir; kullanıcı yeni parolasıyla olağan girişten geçer, ikinci faktör açıksa o adım da gelir.

Geçersiz, kullanılmış ve süresi dolmuş token ayrıştırılmaz: üçü de `400 AUTH_TOKEN_INVALID` döner. Doğrulama hatası (kısa parola) token'ı tüketmez.

| Alternatif | Neden reddedildi |
|---|---|
| Yalnızca parolayı değiştirmek | Ele geçirilmiş oturumlar sürer; sıfırlama saldırganı dışarı atmaz. |
| Sıfırlamadan sonra oturum açıp token dönmek | İkinci faktörü atlatır ve ikinci bir kimlik yolu açar (`AGENTS.md` §15). |
| Yalnızca istek yapılan cihaz dışındaki oturumları kapatmak | Sıfırlama oturumsuz bir akıştır; "bu cihaz" diye bir kavram yok. |
| Bildirim göndermemek | Bağlantısı başkası tarafından kullanılan kullanıcı habersiz kalır. |

## Consequences

### Olumlu
- Sıfırlama, ele geçirilmiş bir hesabı geri almanın gerçek bir yolu.
- Kimlik yolu tek kalır; ikinci faktör sıfırlama ile atlatılamaz.

### Olumsuz
- Kullanıcının bütün cihazlarındaki oturumları kapanır, tekrar giriş yapması gerekir.
- Erişim token'ları en fazla 15 dakika daha geçerli kalır ([ADR-008](008-jwt-auth-with-opaque-refresh.md)): iptal edilemezler, yalnızca yenilenemezler.
- Kullanıcı parolayı belirledikten sonra bir kez daha yazmak zorunda.

### Nötr / Trade-off
- Bildirim e-postası gönderilemezse sıfırlama yine başarılıdır; hata yalnızca loglanır ([ADR-019](019-email-via-smtp-provider-agnostic.md)).

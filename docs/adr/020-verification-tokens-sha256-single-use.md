# [ADR-020] Tek kullanımlık token'lar: 256 bit rastgele, SHA-256 hash'li, süreli

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-account-and-session` design.md D3

---

## Context

[ADR-008](008-jwt-auth-with-opaque-refresh.md) doğrulama ve sıfırlama token'larının "tek kullanımlık, süreli ve hash'li" saklanmasını şart koştu ama hangi hash'in kullanılacağını açık bıraktı. Aynı soru yenileme token'ı ([ADR-018](018-refresh-token-rotation-with-family-revocation.md)) ve ikinci faktör kodu ([ADR-025](025-second-factor-code-bound-to-account.md)) için de geçerli.

## Decision

- Token **256 bit** rastgeledir (`SecureRandom`) ve URL'de taşınabilir biçimdedir (Base64 URL, dolgusuz).
- Veritabanında yalnızca **SHA-256 hash'i** saklanır; arama hash eşitliğiyle, indeksli tek sorguyla yapılır.
- E-posta doğrulama token'ı **24 saat** geçerlidir ve **tek kullanımlıktır** (`used_at`).
- Geçersiz, kullanılmış ve süresi dolmuş token ayrıştırılmaz; üçü de aynı hatayı (`AUTH_TOKEN_INVALID`) alır.
- Üretim ve hash tek bir yardımcıdadır (`SecureTokens`); doğrulama token'ı, yenileme token'ı ve ikinci faktör kodu aynı mekanizmayı kullanır. Parolalar ayrı tutulur ve BCrypt'tir (`PasswordHasher`, maliyet 12).

| Alternatif | Neden reddedildi |
|---|---|
| Token'ı düz metin saklamak | Veritabanını okuyan biri hesapları doğrulayabilir ve oturum devralabilir. |
| BCrypt / Argon2 | Rastgele 256 bit değerde sözlük saldırısı yüzeyi yok; yavaş hash yalnızca maliyet üretir ve eşitlik aramasını imkânsızlaştırır (her satırı tek tek denemek gerekir). |
| İmzalı JWT token (durumsuz) | İptal edilemez ve tek kullanımlık yapılamaz. |
| Üç hata durumunu ayrı kodlarla bildirmek | Hangisi olduğu saldırgana bilgi verir; kullanıcı için çözüm hep aynıdır (yeni bağlantı istemek, [ADR-026](026-verification-link-resend-requires-session.md)). |

## Consequences

### Olumlu
- Veritabanı sızıntısı token'ları kullanılabilir kılmaz.
- Doğrulama sorgusu indeksli ve ucuz kalır.
- Tek yardımcı: yeni bir token türü (parola sıfırlama) yeni mekanizma değil yeni bir tablo ve TTL demek.

### Olumsuz
- Süresi dolmuş token satırları birikir; temizleme işi yok.

### Nötr / Trade-off
- SHA-256 kısa değerlerde güvenli değildir. 6 haneli ikinci faktör kodu bu yüzden kısa ömür, deneme sınırı ve hesaba bağlı hash ile korunur ([ADR-025](025-second-factor-code-bound-to-account.md)); düşük entropi tek başına hash'e emanet edilmez.

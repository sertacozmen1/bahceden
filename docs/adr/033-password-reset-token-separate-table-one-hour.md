# [ADR-033] Parola sıfırlama token'ı: ayrı tablo, 1 saat, yenisi eskileri kapatır

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-password-reset` design.md D1

---

## Context

Parolasını unutan kullanıcı hesabına hiçbir yoldan giremiyordu. Yeni doğrulama bağlantısı da oturum istediği için ([ADR-026](026-verification-link-resend-requires-session.md)) e-postayla kurtarma yolu yoktu.

[ADR-008](008-jwt-auth-with-opaque-refresh.md) sıfırlama token'larının "tek kullanımlık, kısa ömürlü ve hash'li" saklanmasını şart koşmuştu. Ömür, saklama yeri ve birden fazla talebin nasıl ele alınacağı açıktı.

Doğrulama token'ı altyapısı zaten vardı: 256 bit rastgele değer, SHA-256 hash, 24 saat ([ADR-020](020-verification-tokens-sha256-single-use.md)).

## Decision

- Token 256 bit rastgeledir; veritabanında yalnızca **SHA-256 hash'i** saklanır. Düz değer kullanıcıya bir kez, e-postadaki bağlantıda (`<web>/reset-password?token=…`) verilir.
- Ömrü **1 saattir**, **tek kullanımlıktır** (`used_at`).
- Token'lar **ayrı bir tabloda**, `password_reset_tokens` içinde tutulur. Şekli `email_verification_tokens` ile aynıdır.
- Yeni bir talep, hesabın kullanılmamış eski sıfırlama token'larını `used_at` ile kapatır. Aynı anda yalnızca en son bağlantı geçerlidir.
- Onay satırı `SELECT … FOR UPDATE` ile kilitleyerek okur: aynı bağlantıyla gelen iki eşzamanlı istek parolayı iki kez değiştiremez.
- `web` sayfası token'ı açılışta tüketmez. Kullanıcı önce yeni parolayı yazar; böylece bağlantıyı önceden açan e-posta istemcileri token'ı harcamaz.

| Alternatif | Neden reddedildi |
|---|---|
| `email_verification_tokens` tablosuna `purpose` kolonu eklemek | Her sorgu amaca göre filtrelemek zorunda kalır; unutulan bir filtre doğrulama token'ının parola değiştirmesine yol açar. |
| İmzalı, durumsuz token (JWT) | Tek kullanımlık yapılamaz ve iptal edilemez. |
| 24 saatlik ömür (doğrulama token'ı gibi) | Gelen kutusunda uzun süre geçerli kalan bir hesap devralma bağlantısı demektir. |
| Eski token'ları geçerli bırakmak | Daha basit, ama gelen kutusunda biriken birden fazla geçerli devralma bağlantısı bırakır. |
| Token'ı sayfa açılışında tüketmek | Bağlantı önizlemesi yapan e-posta istemcileri token'ı kullanıcıdan önce harcar. |

## Consequences

### Olumlu
- Bir token türünün diğerinin yerine kabul edilmesi şema düzeyinde imkânsız.
- Sızan eski bir sıfırlama e-postası, yenisi istendiği anda işe yaramaz hale gelir.
- Doğrulama token'ıyla aynı araçlar (`SecureTokens`, aynı tablo şekli) kullanıldı; yeni paket yok.

### Olumsuz
- Kullanıcı bağlantıyı bir saat içinde kullanmazsa yeni bağlantı istemek zorunda.
- İki talep arka arkaya yapılırsa ilk e-postadaki bağlantı çalışmaz; kullanıcı yanlış e-postayı açarsa hata görür.

### Nötr / Trade-off
- Süresi dolmuş satırlar birikir; temizleme işi diğer token tablolarıyla aynı borçtur (`00-current-state.md` L15).

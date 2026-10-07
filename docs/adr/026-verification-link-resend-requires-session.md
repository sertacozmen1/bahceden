# [ADR-026] Doğrulama bağlantısını yeniden gönderme oturum ister

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-account-and-session` design.md D9

---

## Context

`account-identity` spec'i süresi dolmuş doğrulama bağlantısı için kullanıcıya "yeni bir bağlantı talep etmesi" gerektiğinin bildirilmesini istiyordu, ama planda bunu yapan bir uç yoktu. Aynı e-postayla yeniden kayıt da bağlantı göndermiyor; yalnızca "hesabınız zaten var" bildirimi gidiyor ([ADR-021](021-identity-responses-do-not-reveal-accounts.md)). Kullanıcı ucun eklenmesini onayladı.

## Decision

- `POST /api/v1/accounts/me/verification-requests` **oturum ister**, gövde almaz ve her durumda `202` döner.
- Adres istekten değil **hesaptan** gelir. E-posta zaten doğrulanmışsa e-posta gönderilmez.
- Önceki bağlantılar süreleri dolana kadar geçerli kalır; hepsi aynı hesabı aynı şekilde işaretler.
- Uç hesap başına saatte 3 istekle sınırlıdır ([ADR-022](022-in-memory-fixed-window-rate-limiting.md)).
- Geçersiz bağlantı mesajı kullanıcıyı giriş yapıp yeni bağlantı istemeye yönlendirir; `web`'de düğme "Hesabım" sayfasındadır.

| Alternatif | Neden reddedildi |
|---|---|
| E-posta alan, herkese açık bir uç | Her durumda aynı yanıt verilse bile başkasının adresine e-posta gönderilebilir; tek savunma hız sınırı olurdu. |
| Aynı e-postayla tekrar kayıt olunca yeni bağlantı göndermek | Kayıt ucunun enumeration güvenli davranışını değiştirir; kayıt ile doğrulamayı karıştırır. |
| Spec senaryosunu gevşetmek | Kullanıcıya çıkış yolu olmayan bir hata mesajı göstermek olurdu. |

## Consequences

### Olumlu
- Hesap keşfi ve üçüncü kişilere e-posta yağdırma yüzeyi baştan yok.
- [ADR-017](017-email-verification-non-blocking-and-2fa-switch.md) sonrası doğrulanmamış kullanıcı da giriş yapabildiği için oturum şartı kimseyi dışarıda bırakmıyor.

### Olumsuz
- Kullanıcı yeni bağlantı için önce giriş yapmak zorunda; parolasını unutmuş biri (sıfırlama henüz yok) bağlantı da isteyemez.

### Nötr / Trade-off
- Eski bağlantıları iptal etmemek, birden fazla geçerli bağlantının aynı anda var olmasına izin verir; hepsi aynı ve zararsız sonucu üretir.

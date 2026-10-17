# add-password-change

**Durum:** Onaylandı (2026-10-17, proje sahibi), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-17).

> Bu change [`00-current-state.md`](../../../../docs/reference/00-current-state.md) L12 sınırlamasını kapatır. `KALANLAR.md`'deki hiçbir maddeye bağlı değildir: yeni paket, yeni dış servis ya da ticari karar gerektirmez. Oturumların kapanma kapsamı proje sahibince karara bağlandı (2026-10-08: "hepsini kapat").

## Why

Oturum açmış kullanıcı parolasını bugün yalnızca e-postadaki sıfırlama bağlantısıyla değiştirebilir ([ADR-035](../../../../docs/adr/035-password-reset-signs-out-everywhere-no-auto-login.md)). Parolası başka bir yerde sızdığı için onu hemen değiştirmek isteyen kullanıcı, önce çıkış yapıp "Parolamı unuttum" akışına girmek ve gelen kutusunu açmak zorunda. Bu, sıradan bir güvenlik işleminin en dolambaçlı yolu.

Gerekli altyapının tamamı `add-password-reset` ile kuruldu: parola kuralları, bütün yenileme token'larını iptal eden sorgu, açık giriş kodlarını kapatan sorgu, "parolanız değişti" e-postası ve giriş ekranındaki bilgi mesajı. Bu dilim onların üzerine oturan en küçük adımdır.

## What Changes

- **`api`:**
  - `POST /api/v1/accounts/me/password-changes` (oturum ister): mevcut parola ve yeni parola alır, **204** döner.
  - Mevcut parola yanlışsa **400** `VALIDATION_FAILED` ve `currentPassword` alanında hata döner. Yeni parola kayıt kurallarına uymuyorsa ya da mevcut parolayla aynıysa `newPassword` alanında hata döner. Hiçbir hata durumunda parola ve oturumlar değişmez.
  - Başarılı değişikliğin etkileri, tek transaction içinde:
    - parola değişir;
    - hesabın **bütün** oturumları, isteği yapan oturum **dahil**, kapanır;
    - açık giriş kodları geçersiz olur;
    - "parolanız değişti" e-postası hesabın dilinde gönderilir (mevcut şablon).
  - Oturum yeniden açılmaz; kullanıcı yeni parolasıyla olağan girişten geçer.
  - E-posta doğrulama durumu **değişmez** (sıfırlamadan farkı: parolayı bilmek gelen kutusuna erişimi kanıtlamaz).
  - Yeni hız sınırı politikası: hesap başına 15 dakikada 5 deneme. Çalınmış bir oturumla mevcut parolaya kaba kuvvet denemesini durdurur.
- **`web`:**
  - `/account/password` sayfası: mevcut parola, yeni parola formu. Hesabım kapısından geçer (oturumsuz istek girişe yönlenir).
  - Hesabım sayfasına "Parolayı değiştir" bağlantısı eklenir.
  - BFF Route Handler `/api/auth/password-change`: erişim token'ıyla `api`'ye gider; **başarıda** oturum çerezlerini siler. Hata durumunda çerezlere dokunmaz.
  - Başarıda form `/login?reset=1` adresine gider; orada mevcut "Parolanız değişti ve açık oturumlarınız kapatıldı" mesajı görünür.
  - Metinler `tr.json` ve `en.json` dosyalarına eklenir.
- **Sözleşme:** bir uç ve bir istek DTO'su (`PasswordChangeRequest`) eklenir. Kırıcı değişiklik yoktur; `openapi.yaml` yeniden üretilir.

## Capabilities

- **Değişen:** `account-identity`. Yeni gereksinim olarak "Oturum açıkken parola değiştirme" eklenir (delta: `specs/account-identity/spec.md`). Mevcut gereksinimler ve senaryolar değişmez.

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `AccountService.changePassword`; `AccountController` (bir uç); `PasswordChangeRequest` DTO'su; `RateLimitPolicy.PASSWORD_CHANGE`; `SecurityConfig` (uç oturum ister); `messages*.properties` (iki alan hatası); sözleşme testi ve `critical` testler. Mevcut `RefreshTokenRepository.revokeAllForAccount`, `LoginChallengeRepository.closeOpenChallenges`, `PasswordHasher` ve `mail.passwordChanged.*` aynen kullanılır |
| `web/` | Evet | `(account)/account/password` sayfası; `ChangePasswordForm` bileşeni; Route Handler `/api/auth/password-change`; `features/account/api.ts` bir çağrı, `bff.ts` bir yol; Hesabım bağlantısı; `messages/*.json`; testler |
| DB | Hayır | Şema değişmez: `accounts.password_hash`, `refresh_tokens`, `login_challenges` mevcut |
| `shared/` | Evet (ekleyici) | Bir uç, bir şema |
| `docs/` | Evet | ADR-087…090, `02-api-reference.md`, `hesap-ve-oturum.md` (bilinçli sınırlar + Tarihçe), `features/index.md`, `00-current-state.md` (L12 kalkar, test ve uç sayıları, sıradaki adım) |

## Bağımlılık

Yeni paket yok (K7).

## Kişisel veri (K10)

Yeni bir kişisel veri alanı yok. Parolalar yalnızca istek gövdesinde taşınır, loglanmaz ve yalnızca BCrypt hash'i saklanır. Kayıtlı adrese giden e-posta türü yeni değil.

## Non-goals

- E-posta adresini değiştirme. Doğrulama ve eski adrese bildirim gerektirir; ayrı bir dilim.
- Yalnızca diğer cihazlardaki oturumları kapatma ya da "oturumlarım" listesi (D3'te reddedildi).
- Parola değişikliğinde ikinci faktör sorma. Oturum zaten girişte ikinci faktörden geçti; ikinci faktör kullanıcı tarafından açılamıyor ([ADR-017](../../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md)).
- Parola gücü ölçer, sızmış parola listesi denetimi (K7: bütçe dışı paket ya da dış servis gerektirir).
- Erişim token'larını anında iptal etme. En fazla 15 dakika geçerli kalmaları ADR-008 ve ADR-035'in kabul ettiği bedel.

## Riskler

- **Çalınmış oturumla parola değiştirme:** saldırgan mevcut parolayı bilmeden değiştiremez; hız sınırı kaba kuvveti durdurur. Bildirim e-postası gerçek sahibe sıfırlama yolunu gösterir.
- **Kullanıcı bütün cihazlarında yeniden giriş yapmak zorunda kalır.** Proje sahibinin bilinçli kararı; parolanın sızdığı senaryoda doğru varsayılan.

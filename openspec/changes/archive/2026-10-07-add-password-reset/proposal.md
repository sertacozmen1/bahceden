# add-password-reset

**Durum:** Onaylandı (2026-10-07, proje sahibi "devam"), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-07).

> Bu change [`00-current-state.md`](../../../../docs/reference/00-current-state.md) L12 sınırlamasını kapatır. `KALANLAR.md`'deki hiçbir maddeye bağlı değildir: yeni paket, yeni dış servis ya da ticari karar gerektirmez.

## Why

Parolasını unutan kullanıcı bugün hesabına hiçbir yoldan giremez. Yeni doğrulama bağlantısı da isteyemez, çünkü o uç oturum ister ([ADR-026](../../../../docs/adr/026-verification-link-resend-requires-session.md)). Tek çıkış yeni bir e-postayla yeni hesap açmak; bu da hesabın geçmişini (ileride siparişlerini) kaybettirir.

Gerekli altyapının tamamı `add-account-and-session` ile kuruldu: hash'li tek kullanımlık token, SMTP gönderimi, hesabın dili, hız sınırlayıcı, BFF ve CSRF savunması. Bu dilim onların üzerine oturan en küçük adımdır.

## What Changes

- **`api`:**
  - `POST /api/v1/accounts/password-resets` (herkese açık): e-posta alır. Yanıt her durumda **202** ve aynı gövdedir. Hesap varsa hesabın dilinde bir sıfırlama bağlantısı gönderilir; yoksa hiçbir şey gönderilmez.
  - `POST /api/v1/accounts/password-resets/confirmations` (herkese açık): token + yeni parola alır, **204** döner. Geçersiz, kullanılmış veya süresi dolmuş token **400** `AUTH_TOKEN_INVALID` döner.
  - Başarılı sıfırlamanın etkileri:
    - parola değişir;
    - hesabın **tüm oturumları** kapanır ve açık giriş kodları geçersiz olur;
    - e-posta doğrulanmış işaretlenir;
    - "parolanız değişti" e-postası gönderilir.
  - Kullanıcı otomatik olarak giriş yapmış olmaz.
  - Token süresi 1 saattir, tek kullanımlıktır ve yalnızca SHA-256 hash'i saklanır. Yeni bir sıfırlama talebi önceki kullanılmamış bağlantıları geçersiz kılar.
  - İki yeni hız sınırı politikası eklenir: talep istemci ve e-posta başına sınırlanır, onay istemci başına sınırlanır.
- **`web`:**
  - `/forgot-password` sayfası: e-posta formu ve her durumda aynı bilgi mesajı.
  - `/reset-password?token=…` sayfası: yeni parola formu. Başarıda `/login`'e yönlendirir ve orada bilgi mesajı gösterir.
  - Giriş formuna "Parolamı unuttum" bağlantısı eklenir.
  - İki BFF Route Handler eklenir: `/api/auth/password-reset-request` ve `/api/auth/password-reset`. Mevcut CSRF başlığı denetimini kullanırlar.
  - Metinler `tr.json` ve `en.json` dosyalarına eklenir.
- **Sözleşme:** iki uç ve iki istek DTO'su eklenir (`PasswordResetRequest`, `PasswordResetConfirmation`). Kırıcı değişiklik yoktur; `openapi.yaml` yeniden üretilir.

## Capabilities

- **Değişen:** `account-identity`. Yeni gereksinim olarak "Parola sıfırlama" eklenir (delta: `specs/account-identity/spec.md`). Mevcut senaryolar değişmez.

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `PasswordResetToken` entity'si ve repository'si; `AccountService` (talep, onay); `AccountController` (iki uç); `RefreshTokenRepository` (hesabın tüm ailelerini iptal); `LoginChallengeRepository` (açık kodları kapatma); `RateLimitPolicy` (iki politika); `SecurityConfig` (iki herkese açık uç); `messages*.properties` (doğrulama, hata, bilgi mesajları ve iki e-posta); sözleşme testi + `critical` testler |
| `web/` | Evet | `(account)/forgot-password`, `(account)/reset-password`, iki Route Handler, `features/account` formları ve `api.ts`, `LoginForm` bağlantısı ve "parola değişti" bilgisi, `messages/*.json`, testler |
| DB | Evet | `006-create-password-reset-tokens.sql`: yeni tablo (`email_verification_tokens` ile aynı şekil); `--rollback` tabloyu düşürür. Ekleyici bir değişikliktir, veri kaybı yoktur |
| `shared/` | Evet (ekleyici) | İki uç, iki şema |
| `docs/` | Evet | ADR-033…036, `02-api-reference.md`, `01-domain-model.md` (ER), `hesap-ve-oturum.md` (sınırlar + Tarihçe), `00-current-state.md` (L12 kalkar, test sayıları) |

## Bağımlılık

Yeni paket yok (K7). Token, e-posta, hız sınırı ve BFF altyapısı mevcut.

## Kişisel veri (K10)

Yeni bir kişisel veri alanı yok. Kayıtlı e-posta adresine yeni bir e-posta türü gidiyor. Log'larda e-posta maskeli yazılır, token hiçbir zaman loglanmaz.

## Non-goals

- Oturum açıkken parola değiştirme (`PATCH /accounts/me/password`). Ayrı ve küçük bir dilim; bu change'in onay ucu ona temel olur.
- Sıfırlamadan sonra otomatik giriş (bkz. design.md D3).
- SMS ile sıfırlama (KALANLAR B4).
- Güvenlik sorusu, yedek kod gibi alternatif kurtarma yolları.
- Süresi dolmuş token satırlarını temizleme işi (L15 ile aynı borç).

## Riskler

- **E-posta kutusu ele geçirilmişse hesap da ele geçirilir.** Bu, e-postayla sıfırlamanın doğasıdır. "Parolanız değişti" bildirimi ve tüm oturumların kapanması zararı sınırlar.
- **Zamanlama farkı:** kayıtlı adres için token üretilip e-posta gönderilir, kayıtsız adres için hiçbir şey yapılmaz. Yanıt süresi farkından hesap keşfi kuramsal olarak mümkündür. Kayıt ucunda da aynı yüzey var (D4, add-account-and-session); hız sınırı bu yüzeyi daraltır. Kalıcı çözüm asenkron gönderimdir, ama kuyruk bugün §3'e aykırı.

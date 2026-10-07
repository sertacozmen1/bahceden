# Tasks: add-password-reset

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema değişikliği ekleyicidir: yalnızca yeni tablo.

## Faz 1 · `api`: talep ve onay uçları

- [x] 1.1 `006-create-password-reset-tokens.sql` (`--rollback` dahil) + `PasswordResetToken` entity'si + `PasswordResetTokenRepository` (hash ile arama; hesabın kullanılmamış token'larını kapatma).
- [x] 1.2 `RefreshTokenRepository`: hesabın bütün ailelerini iptal eden sorgu. `LoginChallengeRepository`: hesabın açık kodlarını kapatan sorgu.
- [x] 1.3 `AccountService.requestPasswordReset` (D1, D2) ve `AccountService.resetPassword` (D3, D4). E-postalar hesabın dilinde; log'larda e-posta maskeli, token hiç yazılmaz.
- [x] 1.4 DTO'lar `PasswordResetRequest` ve `PasswordResetConfirmation` (`@Schema` açıklama + örnek; parola kuralları kayıtla aynı). `AccountController` iki uç; `SecurityConfig` iki herkese açık uç; `RateLimitPolicy.PASSWORD_RESET_REQUEST` (istemci), `PASSWORD_RESET_REQUEST_EMAIL` (e-posta), `PASSWORD_RESET` (onay).
- [x] 1.5 `messages.properties` + `messages_en.properties`: doğrulama, `error.passwordResetTokenInvalid`, `info.passwordResetRequested`, `mail.passwordReset.*`, `mail.passwordChanged.*`.
- [x] 1.6 `openapi.yaml` yeniden üretilir (`OpenApiContractTest`).
- [x] 1.7 **Test (`critical`):** spec'teki her senaryo:
  - kayıtlı ve kayıtsız adreste aynı yanıt ve e-posta farkı;
  - yeni talep eski token'ı kapatır;
  - yeni parolayla giriş olur, eskiyle olmaz;
  - yenileme token'ı ve giriş kodu reddedilir;
  - yanıtta token yok;
  - `email_verified` işaretlenir;
  - bildirim e-postası hesabın dilinde gider;
  - geçersiz, kullanılmış ve süresi dolmuş token için tek hata (sabit `Clock`);
  - kurala uymayan parola token'ı tüketmez;
  - iki hız sınırı.
- [x] 1.8 **Doğrulama:** `./mvnw -B verify` temiz.

## Faz 2 · `web`: iki ekran ve BFF

- [x] 2.1 `npm run gen:api`; `features/account/api.ts` iki çağrı; Route Handler'lar `/api/auth/password-reset-request` ve `/api/auth/password-reset` (CSRF başlığı, `Accept-Language` iletimi, hata gövdesi eşlemesi).
- [x] 2.2 `(account)/forgot-password`: e-posta formu; gönderimden sonra her durumda aynı bilgi mesajı.
- [x] 2.3 `(account)/reset-password`: token yoksa yönlendirici hata durumu; yeni parola formu; başarıda `/login?reset=1` ve giriş ekranında bilgi mesajı; geçersiz token hatası yeni bağlantı isteme yoluyla birlikte gösterilir.
- [x] 2.4 `LoginForm`'a "Parolamı unuttum" bağlantısı; metinler `tr.json` + `en.json`.
- [x] 2.5 **Test:** iki form için doğrulama hatası, sunucu hatası eşlemesi ve başarı yolu; Route Handler'lar CSRF başlığı olmadan reddeder; anahtar eşitliği testi yeşil.
- [x] 2.6 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 3 · Uçtan uca doğrulama ve doküman

- [x] 3.1 `docker compose up --build`: talep → mailpit'te bağlantı → yeni parola → eski oturum yenilenemez → yeni parolayla giriş. İki dilde e-posta içeriği kontrolü.
- [x] 3.2 Ekranların 375 ve 1440 px'de, açık ve koyu temada başsız tarayıcı ekran görüntüsü.
- [x] 3.3 ADR-033…036 + `docs/adr/index.md`.
- [x] 3.4 `docs/reference/02-api-reference.md`, `01-domain-model.md` (ER), `docs/features/hesap-ve-oturum.md` (bilinçli sınırlar + Tarihçe), `00-current-state.md` (L12 kalkar, test sayıları, sıradaki adım).
- [x] 3.5 Delta spec ana spec'e sync edilir.
- [x] 3.6 Archive kullanıcı onayıyla yapıldı (2026-10-07).

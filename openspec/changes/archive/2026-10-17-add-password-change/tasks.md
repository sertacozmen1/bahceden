# Tasks: add-password-change

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema değişikliği yok.

## Faz 1 · `api`: parola değiştirme ucu

- [x] 1.1 `PasswordChangeRequest` DTO'su (`currentPassword`, `newPassword`; `@Schema` açıklama + örnek; `newPassword` kayıt kurallarıyla aynı kısıtlar).
- [x] 1.2 `AccountService.changePassword(accountId, currentPassword, newPassword)` (D1–D3): mevcut parola denetimi, aynı parola denetimi, parola değişimi, `revokeAllForAccount`, `closeOpenChallenges`, `mail.passwordChanged.*` gönderimi. Alan hataları mevcut `fieldErrors` yoluyla; parolalar loglanmaz.
- [x] 1.3 `AccountController`: `POST /api/v1/accounts/me/password-changes` (204; 400/401/429 yanıtları belgeli); `SecurityConfig` ucu `authenticated()` işaretler; `RateLimitPolicy.PASSWORD_CHANGE` (hesap başına 15 dakikada 5), denetimden önce uygulanır.
- [x] 1.4 `messages.properties` + `messages_en.properties`: `validation.currentPassword.required`, `validation.currentPassword.invalid`, `validation.newPassword.sameAsCurrent` (ve gerekirse `newPassword` kısıt anahtarları). `MessagesCatalogTest` yeşil.
- [x] 1.5 `openapi.yaml` yeniden üretilir (`OpenApiContractTest`).
- [x] 1.6 **Test (`critical`):** spec'teki her `api` senaryosu:
  - yeni parolayla giriş olur, eskiyle olmaz;
  - değişikliği yapan oturumun ve ikinci bir oturumun yenileme token'ı reddedilir; açık giriş kodu reddedilir;
  - yanıtta token yok;
  - bildirim e-postası hesabın dilinde gider;
  - `email_verified` değişmez;
  - yanlış mevcut parola, kurala uymayan ve mevcutla aynı yeni parola: alan hatası, oturumlar açık kalır;
  - oturumsuz istek 401;
  - hız sınırı doğru parolayı da reddeder.
- [x] 1.7 **Doğrulama:** `./mvnw -B verify` temiz (318 test, 0 hata; 9 yeni `critical` test `PasswordChangeTest`).

## Faz 2 · `web`: sayfa, form ve BFF

- [x] 2.1 `npm run gen:api`; `features/account/api.ts` `changePassword` çağrısı; `bff.ts` yol; Route Handler `/api/auth/password-change` (CSRF başlığı, erişim token'ı çerezden, başarıda `clearSession`, hatada çerezlere dokunmaz).
- [x] 2.2 `ChangePasswordForm`: iki alan, sonucu önceden söyleyen not, sunucu alan hatalarının eşlenmesi, başarıda `/login?reset=1` ve `router.refresh()` (üst başlık çerezsiz yeniden çizilir; çıkış düğmesiyle aynı yol).
- [x] 2.3 `(account)/account/password` sayfası (yükleniyor durumu üstteki `account/loading.tsx` iskeletinden gelir; ayrı dosya gerekmedi); Hesabım'a "Güvenlik" bölümü ve "Parolayı değiştir" bağlantısı; metinler `tr.json` + `en.json`.
- [x] 2.4 **Test:** form için doğrulama hatası, sunucu alan hatası eşlemesi (mevcut parola), başarı yolu; Route Handler başarıda çerezleri siler, hatada silmez, CSRF başlığı olmadan reddeder; Hesabım bağlantısı; anahtar eşitliği testi yeşil.
- [x] 2.5 **Doğrulama:** `lint`, `typecheck`, `test` (275 test, 69 dosya), `build` temiz.

## Faz 3 · Uçtan uca doğrulama ve doküman

- [x] 3.1 `docker compose up -d --build`: iki tarayıcı oturumu aç → birinden parolayı değiştir → giriş ekranı mesajı → ikinci oturum yenilenemez → mailpit'te bildirim → yeni parolayla giriş. Yanlış mevcut parola alan hatası. (İki oturumlu adımlar `api`'ye doğrudan istekle, form akışı uygulama içi tarayıcıda yapıldı; iki yol da beklenen sonucu verdi, e-posta doğrulama durumu değişmedi.)
- [x] 3.2 Sayfanın 375 px koyu ve 1440 px açık temada ekran görüntüsü; iki genişlikte yatay taşma yok, alanlar ve düğme 44 px. Konsolda yalnızca kasıtlı yanlış parola denemesinin 400 ağ kaydı var.
- [x] 3.3 ADR-087…090 + `docs/adr/index.md` + `docs/.tools/mkdocs.yml`.
- [x] 3.4 `docs/reference/02-api-reference.md`, `docs/features/hesap-ve-oturum.md` (bilinçli sınırlar + Tarihçe), `docs/features/index.md`, `00-current-state.md` (L12 kalkar; test ve uç sayıları; sıradaki adım: PR #6 maddesi düşer). `./docs/.tools/serve.sh --build` temiz.
- [x] 3.5 Delta spec ana spec'e sync edilir.
- [x] 3.6 Archive kullanıcı onayıyla yapıldı (2026-10-17).

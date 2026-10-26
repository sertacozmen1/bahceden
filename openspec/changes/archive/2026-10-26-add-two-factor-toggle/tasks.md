# Tasks: add-two-factor-toggle

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema değişmez.

## Faz 1 · `api`: ikinci adımı açan ve kapatan uç (D1)

- [x] 1.1 `dto/TwoFactorUpdate` (`@NotNull Boolean enabled`, `@NotBlank @Size(max=200) currentPassword`); `AccountView.twoFactorEnabled` + `AccountMapper`.
- [x] 1.2 `Account.useTwoFactor(boolean, Instant)`; `ApiErrorCode.TWO_FACTOR_EMAIL_NOT_VERIFIED` (422); `RateLimitPolicy.TWO_FACTOR_CHANGE` (5 / 15 dk).
- [x] 1.3 `AccountService.changeTwoFactor(accountId, enabled, currentPassword)`: sıra D1'deki gibi; kapatırken `closeOpenChallenges`; bildirim e-postası.
- [x] 1.4 `AccountController` `PUT /api/v1/accounts/me/two-factor` → `204`; `SecurityConfig` `authenticated()`; OpenAPI anotasyonları.
- [x] 1.5 `messages.properties` + `messages_en.properties`: `mail.twoFactorEnabled.*`, `mail.twoFactorDisabled.*`, `error.twoFactorEmailNotVerified`, doğrulama anahtarları. `MessagesCatalogTest` yeşil.
- [x] 1.6 `OpenApiContractTest -Dopenapi.write=true` ile `openapi.yaml` yeniden üretilir.
- [x] 1.7 **Test (`critical`, `TwoFactorToggleTest`):** design.md "Test etkisi" listesi. Mevcut `AccountView` testleri yeni alanla. (9 test; ek olarak "doğrulanmamış e-postayla kapatma serbesttir" senaryosu sabitlendi. Mevcut testler alan eklenince değişmeden yeşil.)
- [x] 1.8 **Doğrulama:** `./mvnw -B verify` temiz (356 test, 0 hata; sözleşmeye yalnızca ekleme, 70 satır).

## Faz 2 · `web`: Hesabım ve ikinci adım sayfası (D2)

- [x] 2.1 `npm run gen:api`; `features/account/api.ts` `changeTwoFactor`.
- [x] 2.2 BFF `app/api/account/two-factor/route.ts` (`PUT`) + test.
- [x] 2.3 `TwoFactorForm` + `/account/two-factor` sayfası (`loading.tsx`, `error.tsx` mevcut desende); doğrulanmamış e-postada açıklama + `ResendVerificationButton`. (Parola sayfası gibi `/account/loading.tsx` ve `(account)/error.tsx` sınırlarını kullanır; ayrı dosya gerekmedi. E-postası doğrulanmamış ama ikinci adımı açık kullanıcıya kapatma formu gösterilir.)
- [x] 2.4 Hesabım Güvenlik bölümü: durum + bağlantı.
- [x] 2.5 `messages/tr.json` + `en.json`; testler (form, sayfa, Hesabım). (12 yeni test; `AccountView` alanı eklendiği için 8 test dosyasındaki hesap sabitlerine `twoFactorEnabled: false` eklendi.)
- [x] 2.6 **Doğrulama:** `npm run lint`, `typecheck`, `test`, `build` temiz (77 dosya, 315 test).

## Faz 3 · Uçtan uca doğrulama ve doküman

- [x] 3.1 `docker compose up -d --build`: doğrulanmış dev hesabıyla ikinci adımı aç → mailpit'te bildirim → çıkış → giriş kod ister → kodla giriş → kapat → giriş tek adım. Doğrulanmamış hesapta form yerine doğrulama yönlendirmesi. (`alici@ornek.test`, tarayıcıda: yanlış parola alanın altında; doğru parolayla "Açık" + "iki adımlı giriş açıldı" e-postası; çıkış → giriş kod ekranı → mailpit'teki kodla giriş, `next` sayfasına dönüş; kapatma → "kapatıldı" e-postası, `api` girişi 200 tek adım. Hesabım'da "İki adımlı giriş · Kapalı" bağlantısı. Doğrulanmamış `dil-senkron@ornek.test`: form yok, doğrulama yönlendirmesi. Dev veritabanında `alici@ornek.test`'in ikinci adımı yeniden kapalı.)
- [x] 3.2 ADR-103, ADR-104 + `docs/adr/index.md` + `docs/.tools/mkdocs.yml`; ADR-017 ve ADR-025'e not.
- [x] 3.3 `docs/reference/02-api-reference.md`; `docs/features/hesap-ve-oturum.md` (Tarihçe, bilinçli sınırlar), `docs/features/index.md`, `00-current-state.md` (L13 kalkar, test sayıları, sıradaki adım). `./docs/.tools/serve.sh --build` temiz.
- [x] 3.4 Delta spec ana spec'e sync edilir.
- [x] 3.5 Archive kullanıcı onayıyla yapıldı (2026-10-26).

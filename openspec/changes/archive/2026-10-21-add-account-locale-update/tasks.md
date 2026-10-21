# Tasks: add-account-locale-update

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema değişmez.

## Faz 1 · `api`: hesabın dilini değiştiren uç

- [x] 1.1 `dto/AccountLocaleUpdate` record: `@NotNull SupportedLocale locale`, Türkçe `@Schema` açıklaması ve örneği (D1).
- [x] 1.2 `AccountService.changeLocale(accountId, locale)`: mevcut `Account.useLocale` ile; hesap `currentAccount` ile okunur (yoksa mevcut desendeki `401`).
- [x] 1.3 `AccountController`: `PUT /api/v1/accounts/me/locale` → `204`; OpenAPI anotasyonları (`204`, `400`, `401`). `SecurityConfig`: `authenticated()`.
- [x] 1.4 `OpenApiContractTest -Dopenapi.write=true` ile `openapi.yaml` yeniden üretilir (K8: elle düzenlenmez).
- [x] 1.5 **Test (`critical`, `AccountLocaleTest`):**
  - dil `EN` olur ve ardından istenen doğrulama e-postası İngilizce gider;
  - aynı dil `updated_at`'i değiştirmez;
  - geçersiz değer ve eksik alan `400`;
  - token yok `401`;
  - başka hesabın dili değişmez.
- [x] 1.6 **Doğrulama:** `./mvnw -B verify` temiz (343 test, 0 hata; 5 yeni `AccountLocaleTest` testi; sözleşmeye yalnızca yeni uç ve `AccountLocaleUpdate` şeması eklendi).

## Faz 2 · `web`: dil menüsü hesabı eşitler

- [x] 2.1 `npm run gen:api`; `features/account/api.ts` `updateAccountLocale(accessToken, locale)`.
- [x] 2.2 `app/api/account/locale/route.ts` (`PUT`, D2): oturum yoksa `204`; `api` `401` → `204`; tanınmayan değer `400`. Route Handler testi.
- [x] 2.3 `LocaleMenu`: çerez → BFF → `router.refresh()`; BFF hatası yenilemeyi engellemez. Bileşen testi güncellenir.
- [x] 2.4 **Doğrulama:** `npm run lint`, `typecheck`, `test`, `build` temiz (285 test; 6 yeni Route Handler testi, 2 yeni `LocaleMenu` testi). Ek: tanınmayan değer için `routeSupport.invalidRequestResponse` eklendi; "gövde okunamadı" yanıtıyla aynı gövdeyi üretir.

## Faz 3 · Uçtan uca doğrulama ve doküman

- [x] 3.1 `docker compose up -d --build`: Türkçe hesapla giriş → menüden English → yeni doğrulama bağlantısı iste → mailpit'te İngilizce e-posta; veritabanında `accounts.locale = 'en'`. Oturumsuz dil değişikliği `api`'ye gitmez. (Yeni dev hesabı `dil-senkron@ornek.test`: kayıt e-postası Türkçe; menüden English → `PUT /api/account/locale` 204, `accounts.locale` `en`; yeni bağlantı "Bahçeden: verify your email address". Çıkıştan sonra menüden Türkçe → BFF 204, `api` log'unda istek yok, hesabın dili ve `updated_at` değişmedi.)
- [x] 3.2 ADR-098, ADR-099 + `docs/adr/index.md` + `docs/.tools/mkdocs.yml`; ADR-032'ye not.
- [x] 3.3 `docs/reference/` API sayfası; `docs/features/hesap-ve-oturum.md` Tarihçe; `docs/features/index.md`; `00-current-state.md` (L19 kalkar, test sayısı, sıradaki adım). `./docs/.tools/serve.sh --build` temiz.
- [x] 3.4 Delta spec ana spec'e sync edilir.
- [x] 3.5 Archive kullanıcı onayıyla yapıldı (2026-10-21).

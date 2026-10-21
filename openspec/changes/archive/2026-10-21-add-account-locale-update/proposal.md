# add-account-locale-update

**Durum:** Onaylandı (2026-10-21, proje sahibi), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-21).

> Bu change [`00-current-state.md`](../../../../docs/reference/00-current-state.md) L19 sınırlamasını kapatır. [ADR-032](../../../../docs/adr/032-account-locale-stored-and-refreshed-on-login.md) "ayrı dil tercihi ucu"nu, ekranı olmadığı için reddetmiş ve "ekran gelince ayrı karar" demişti. Dil menüsü artık var ([ADR-030](../../../../docs/adr/030-locale-from-cookie-then-header.md)). `KALANLAR.md`'deki hiçbir maddeye bağlı değildir.

## Why

Hesabın dili kayıtta yazılır ve yalnızca girişte güncellenir (ADR-032). Oturum açıkken başlıktaki dil menüsünden İngilizceye geçen kullanıcı, bir sonraki girişine kadar e-postalarını eski dilde alır. Bu e-postalar şunlardır:

- yeniden istenen doğrulama bağlantısı;
- sipariş, kargo, teslim ve talep e-postaları;
- parola değişti bildirimi.

Oturumlar 30 güne kadar sürdüğü için bu gecikme haftalarca sürebilir. Kullanıcı ekranda İngilizce görür, e-postada Türkçe okur.

## What Changes

- **`api`:**
  - Yeni uç `PUT /api/v1/accounts/me/locale`, gövde `{"locale": "EN"}`, yanıt `204`.
  - Yalnızca oturum sahibinin hesabını değiştirir.
  - Dil aynıysa kayıt değişmez.
  - Geçersiz değer `400` döner.
  - Mevcut `Account.useLocale` yeniden kullanılır (K9).
- **`web`:**
  - Dil menüsü seçimi önce çereze yazar (bugünkü gibi), sonra yeni BFF ucunu `PUT /api/account/locale` çağırır, ardından sayfayı yeniler.
  - BFF oturum yoksa `api`'ye hiç gitmez ve `204` döner.
  - Erişim token'ı geçmişse (`401`) de `204` döner: ekrandaki dil yine değişir, hesabın dili bir sonraki girişte yetişir (ADR-032).
  - Eşitleme başarısız olursa dil değişikliği geri alınmaz; kullanıcıya hata gösterilmez.
- **Sözleşme:**
  - Yeni uç;
  - yeni şema `AccountLocaleUpdate` ve `locale` alanı için `TR` / `EN` enum'u (`SupportedLocale`).
  - Ekleyici değişiklik; kırıcı değil.

## Capabilities

- **Değişen:** `localization`. "Hesabın dili ve e-postalar" gereksinimi, dil menüsünden yapılan seçimin hesabın dilini de güncellemesini içerecek biçimde değişir. İki senaryo eklenir: oturum açıkken dil değişir; oturum yokken dil değişir (delta: `specs/localization/spec.md`).

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `AccountController` yeni uç; `dto/AccountLocaleUpdate`; `AccountService.changeLocale`; `SecurityConfig` (kimlik doğrulamalı); yeni `AccountLocaleTest` (`critical`: yetkilendirme) |
| `web/` | Evet | `features/account/api.ts` `updateAccountLocale`; yeni Route Handler `app/api/account/locale/route.ts` + testi; `LocaleMenu` eşitleme çağrısı + testi. Yeni metin yok |
| DB | Hayır | `accounts.locale` zaten var (`005`) |
| `shared/` | Evet | `openapi.yaml` yeniden üretilir: yeni uç ve şema |
| `docs/` | Evet | ADR-098, ADR-099; ADR-032'ye not; `docs/reference/` API sayfası; `hesap-ve-oturum.md` Tarihçe; `00-current-state.md` (L19 kalkar, test sayısı) |

## Bağımlılık

Yeni paket yok (K7).

## Kişisel veri (K10)

Yeni kişisel veri alanı yok. Dil tercihi tek başına kişiyi tanımlamaz ve log'a yazılmaz (ADR-032).

## Non-goals

- Hesabım'da ayrı bir "e-posta dili" ayarı. Tek dil tercihi var: arayüzün dili.
- Girişte dil güncellemesini kaldırmak. Başka cihazda giriş yapan kullanıcı için o cihazın dili yine yazılır (ADR-032 değişmez).
- Hesabın dilini başka cihazlara taşımak (girişte çerezi hesabın diline çevirmek). Cihaz tercihi cihazda kalır.
- Üçüncü dil.

## Riskler

- **İki cihaz:** kullanıcı iki cihazda farklı dil kullanırsa hesabın dili son seçimi veya son girişi izler. Bu ADR-032'deki "en son kullanılan dil" kuralının doğal sonucudur.
- **Anonim ziyaretçi:** her dil seçimi `204` dönen küçük bir BFF isteği daha yapar; `api`'ye gitmez.

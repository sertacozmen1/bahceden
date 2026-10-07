# [ADR-029] `web` çevirisi: next-intl, yönlendirmesiz; metinler ICU biçiminde JSON'da

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-i18n` design.md D1 · `openspec/KALANLAR.md` A4'ün çoklu dil sorusunu karara bağlar

---

## Context

`web`'in bütün metinleri tek bir Türkçe modülde duruyordu (`web/src/lib/texts.ts`); çoklu dil `openspec/KALANLAR.md` A4 ile kapsam dışıydı. Proje sahibi Türkçe + İngilizce desteği ve sektör standardı bir i18n altyapısı istedi; URL'de dil öneki istemedi ([ADR-030](030-locale-from-cookie-then-header.md)).

İhtiyaçlar:
- aynı API ile hem Server Component'te hem istemcide çeviri;
- çoğul, seçim ve yer tutucu (ICU);
- tip güvenli anahtarlar;
- istek başına dil seçimi.

## Decision

- **Kütüphane:** `next-intl` **4.14.9** (MIT, `web/` runtime, pin'li), yönlendirmesiz kurulum. `src/i18n/request.ts` istek başına dili ve mesajları verir; kök düzen `NextIntlClientProvider` ile istemci bileşenlerine geçirir.
- **Mesaj dosyaları:** `web/messages/tr.json` (kaynak dil) ve `en.json`, alan bazlı ad alanlarıyla (`storefront`, `account.login` …) ve ICU biçiminde:
  - çoğul: `{count, plural, one {# product} other {# products}}`
  - seçim: birim ve rol adları `select`
  - yer tutucu: `{city}`
  - `texts.ts` kaldırıldı.
- **Tip güvenliği:** `AppConfig` anahtarları `tr.json`'un tipinden alır. `en.json`'un anahtar kümesinin `tr.json` ile birebir aynı olduğunu bir test zorunlu kılar (`src/i18n/messages.test.ts`).
- **Biçimleme:** para ve sayı seçili dile göre `Intl` ile yapılır. `money.ts` dil parametresi alır; para simgesi her dilde dar biçimde (`₺`).
- **Eklenti kullanılmıyor:** next-intl'in `createNextIntlPlugin` sarmalayıcısı kullanılmaz. `next.config.ts` yalnızca `next-intl/config` takma adını Turbopack'e verir.
  - Sarmalayıcı, kullanmadığımız deneysel mesaj çıkarma özelliği için `@swc/core` ve `@parcel/watcher` yerel ikililerini yapılandırma yüklenirken yüklüyor.
  - Geliştirme makinesinde SWC'nin yerel ikili önbellek dizini izin denetimine takıldı; `next typegen` ve `next build` açılamadı.
  - Bu kurulumda sarmalayıcının yaptığı tek iş o takma addır.
- **Testler:** gerçek mesaj dosyalarıyla render edilir (`src/test/intl.tsx`); `next-intl/server` test kurulumunda aynı dosyalarla taklit edilir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `react-i18next` + `i18next` | Server Component desteği eklentiyle geliyor, iki paket; App Router'da next-intl kadar yerleşik değil. |
| Kendi katmanımız (`texts.ts` dil başına) | Paket yok, ama ICU çoğul/seçim ve istek başına dil seçimi elle yazılırdı; proje sahibi sektör standardını seçti. |
| next-intl yönlendirmeli kurulum (`/en/...`) | Proje sahibi öneksiz URL seçti ([ADR-030](030-locale-from-cookie-then-header.md)). |
| `createNextIntlPlugin` sarmalayıcısı | Yerel ikili yüklüyor; bu kurulumda tek katkısı bir takma ad. |

## Consequences

### Olumlu
- Yeni bir ekran metni kod içine değil mesaj dosyasına yazar; olmayan anahtar derlemede yakalanır.
- Yeni dil, bir JSON dosyası ve dil listesinde bir satırdır.
- Server Component'ler çeviriyi sunucuda yapar; istemciye yalnızca istemci bileşenlerinin ihtiyacı olan mesajlar gider.

### Olumsuz
- `web/` runtime bütçesine bir paket eklendi. Paketin `@swc/core` ve `@parcel/watcher` bağımlılıkları kurulur ama yüklenmez.
- Sarmalayıcı kullanılmadığı için next-intl'in ileride sarmalayıcıya ekleyeceği yapılandırmayı biz takip etmeliyiz; sürüm yükseltmesinde `next.config.ts` gözden geçirilir.
- Testler bir sağlayıcı sarmalayıcısıyla render edilir.

### Nötr / Trade-off
- Tip güvenliği yalnızca anahtarlar içindir; ICU argümanları (örn. `count`) derlemede denetlenmez. Çoğul ve seçim biçimleri testle sabitlenir.

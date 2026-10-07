# add-i18n

**Durum:** Onaylandı ve uygulanıyor (2026-10-07). Kararlar proje sahibiyle konuşmada verildi: diller Türkçe + İngilizce; URL'de dil öneki yok; `web` için sektör standardı kütüphane (next-intl); `api` metinleri de `Accept-Language` ile çevrilir ve hesabın dili saklanır.

> Bu change [KALANLAR A4](../../../KALANLAR.md)'ün **çoklu dil** sorusunu karara bağlar (Türkçe + İngilizce). A4'ün teslimat bölgesi ve mobil uygulama soruları açık kalır.

## Why

Bugün `web` ve `api` yalnızca Türkçe konuşuyor. Metinler tek yerde toplandığı için (`web/src/lib/texts.ts`, `api` içindeki sabitler) bu dilim kapıyı açık tutmuştu (AGENTS.md §12.4). Proje sahibi İngilizce desteği ve kalıcı bir i18n altyapısı istedi.

Altyapı şimdi kurulmazsa sonraki her ekran (satıcı paneli, sepet, sipariş) metni tek dile gömerek yazılır ve geri dönüşü pahalılaşır.

## What Changes

- **`web`:**
  - next-intl "yönlendirmesiz" kurulumla gelir. Dil URL'de görünmez; sırasıyla `bahceden_locale` çerezinden, tarayıcının `Accept-Language` başlığından, ikisi de yoksa varsayılan Türkçeden seçilir.
  - `texts.ts` kaldırılır. Tüm kullanıcı metinleri `web/messages/tr.json` ve `en.json` içinde ICU biçimindedir (çoğul, seçim, yer tutucu).
  - Başlığa, tema menüsünün yanına tek ikonlu bir dil menüsü eklenir.
  - Para biçimi seçili dile göre yapılır.
  - `api` çağrıları seçili dili `Accept-Language` ile iletir.
- **`api`:**
  - Kullanıcıya dönen her metin `messages.properties` (Türkçe, temel) ve `messages_en.properties` içinden, isteğin `Accept-Language`'ına göre çözülür: doğrulama mesajları, hata mesajları, yanıt mesajları ve e-postalar.
  - Hata **kodları** değişmez; yalnızca `message` dili değişir.
- **Hesabın dili:**
  - `accounts.locale` kolonu eklenir (yeni changeset, varsayılan `tr`).
  - Kayıtta isteğin dili yazılır; her başarılı girişte (parola adımı) güncellenir.
  - Hesaba giden e-postalar (doğrulama bağlantısı, giriş kodu, "hesabınız zaten var") bu dilde gönderilir.
- **Sözleşme:** yapısal değişiklik yok. Hata gövdesindeki `message` alanının açıklaması "isteğin dilinde" olarak güncellenir; `openapi.yaml` yeniden üretilir.

## Capabilities

- **Yeni:** `localization`: dil seçimi, desteklenen diller, `api` mesajlarının dili, hesabın dili.
- **Değişen:** yok. `account-identity` senaryoları dil bağımsızdır; e-postanın dili `localization` spec'inde tanımlanır.

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `common/i18n/` (desteklenen diller, dil çözümleyici), `messages*.properties`, `ApiException` mesaj anahtarı taşır, `GlobalExceptionHandler` ve `ApiAuthenticationEntryPoint` mesajı çözer, DTO doğrulama mesajları anahtara döner, `AccountService`/`SessionService` e-postaları hesabın dilinde gönderir, `Account.locale` |
| `web/` | Evet | next-intl kurulumu (`next.config.ts`, `src/i18n/`), `messages/tr.json` + `en.json`, tüm bileşen ve Route Handler'lar, `money.ts` dil parametresi, `apiClient` `Accept-Language` iletir, `LocaleMenu`, testler sağlayıcıyla |
| DB | Evet | `005-account-locale.sql`: `accounts.locale VARCHAR(8) NOT NULL DEFAULT 'tr'` + `CHECK`; `--rollback` kolonu düşürür; mevcut satırlar `tr` olur (veri kaybı yok) |
| `shared/` | Evet (yalnızca açıklama) | `ApiError.message` açıklaması; yapı değişmez |
| `docs/` | Evet | ADR-029…032, KALANLAR A4 güncellemesi, AGENTS.md (§1 dil, §7 bütçe, §11.5, §12.4), referans sayfaları, `_genel.md` Tarihçe |

## Bağımlılık talebi (K7, onaylandı)

`next-intl` **4.14.9** (MIT), `web/` runtime.

1. **Neden platform yetmiyor:** Next.js çeviri altyapısı vermiyor. Server Component'te ve istemcide aynı çeviri API'si, ICU çoğul/seçim biçimi, tip güvenli anahtar ve istek başına dil seçimi elle yazılırsa kendi i18n kütüphanemizi yazmış oluruz.
2. **Alternatifler:**
   - `react-i18next` + `i18next`: App Router Server Component desteği eklentilerle geliyor, iki paket.
   - Kendi katmanımız: ICU çoğul ve biçimleme elle yazılır.
   - Proje sahibi "sektör standardı"nı seçti.
3. **Yük:** Kendisi küçük. Bağımlılıkları arasında mesaj çıkarma aracı için `@swc/core` ve `@parcel/watcher` (yerel ikili) var; bunlar yalnızca `node_modules`'a girer, istemci paketine girmez. Bakım aktif (son sürüm 2026-10-02).
4. **Katman:** yalnızca `web/`; bağımlılık yönü değişmez.

`api` tarafında yeni paket yok: Spring `MessageSource` ve `LocaleResolver` çekirdekte.

## Non-goals

- URL'de dil öneki (`/en/...`) ve dil başına arama motoru indekslemesi (proje sahibi öneksiz seçti).
- İngilizce rota adları: `/giris`, `/kayit` aynı kalır.
- Üçüncü dil, sağdan sola yazım.
- Ürün adları ve satıcı adları gibi veri içeriğinin çevirisi: bunlar kullanıcı verisidir, çevrilmez.
- OpenAPI açıklamalarının (`@Schema`) çevrilmesi: geliştirici dokümanıdır, Türkçe kalır.
- Hesap dilini doğrudan değiştiren bir uç (`PATCH /accounts/me`): dil girişte güncellenir; ayrı uç ihtiyaç doğunca.
- Log mesajlarının çevrilmesi: log'lar Türkçe kalır.

## Riskler

- **Çeviri eksikliği:** İngilizce dosyada eksik anahtar sessizce Türkçe metne veya anahtar adına düşebilir. Bir test iki dosyanın anahtar kümesinin eşitliğini zorunlu kılar.
- **Başlık genişliği:** 375px başlığa dördüncü bir öğe ekleniyor. Dar ekranda marka yazısı gizlenir, logo kalır.
- **Hesap dili:** yalnızca girişte güncellenir. Oturum açıkken dili değiştiren kullanıcının sonraki e-postası bir sonraki girişe kadar eski dilde gider.

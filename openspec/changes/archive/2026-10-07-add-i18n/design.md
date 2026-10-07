# Design: add-i18n

Bağlam ve kapsam için [proposal.md](proposal.md). Her `D<n>` archive'dan önce `docs/adr/` altında ADR olur (K1).

## Kararlar

### D1 · `web`: next-intl, yönlendirmesiz; metinler ICU biçiminde JSON'da

- next-intl "without i18n routing" kurulumuyla gelir:
  - `src/i18n/request.ts` istek başına dili ve mesajları verir;
  - kök düzen `NextIntlClientProvider` ile istemci bileşenlerine aynı mesajları geçirir;
  - `next.config.ts` `next-intl/config` modülünü Turbopack takma adıyla `src/i18n/request.ts`'e bağlar.
- **Uygulama sırasında değişti (2026-10-07):** next-intl'in `createNextIntlPlugin` sarmalayıcısı kullanılmaz.
  - **Sorun:** sarmalayıcı, kullanmadığımız deneysel mesaj çıkarma özelliği için `@swc/core` ve `@parcel/watcher` yerel ikililerini yapılandırma yüklenirken yüklüyor. Geliştirme makinesinde SWC'nin yerel ikili önbellek dizini izin denetimine takıldı ve `next typegen` ile `next build` açılamadı.
  - **Yeterli olan:** sarmalayıcının bu kurulumda yaptığı tek iş `next-intl/config` takma adıdır (`trailingSlash` ve `basePath` kullanılmıyor). Bu takma ad doğrudan yazıldı.
  - **Sonuç:** paket bağımlılık olarak kalır; yerel ikilileri `node_modules`'ta durur ama hiç yüklenmez.
- Metinler `web/messages/tr.json` (kaynak dil) ve `en.json` içinde, alan bazlı ad alanlarıyla durur (`storefront`, `account.login` …). Biçim ICU:
  - çoğul: `{count, plural, one {# product} other {# products}}`
  - seçim: birim ve rol adları `select`
  - yer tutucu: `{city} üreticisi`
- Anahtarlar tip güvenlidir. `AppConfig.Messages` `tr.json`'un tipinden türetilir; olmayan anahtar derlemede hata verir.
- `en.json`'un anahtar kümesi `tr.json` ile **birebir aynıdır**; bunu bir test zorunlu kılar.
- Para ve sayı biçimi seçili dile göre `Intl` ile yapılır. `money.ts` dil parametresi alır; tek biçimleme yeri olarak kalır (AGENTS.md §12.3).

**Gerekçe:** App Router'da Server Component ve istemci için aynı API, ICU ve tip güvenliği hazır geliyor; proje sahibi sektör standardını seçti.

**Alternatifler:** `react-i18next` (Server Component desteği eklentiyle, iki paket); kendi katmanımız (ICU elle); next-intl yönlendirmeli kurulum (proje sahibi öneksiz URL seçti).

### D2 · Dil seçimi: çerez → `Accept-Language` → Türkçe; aynı dil `api`'ye iletilir

- **Desteklenen diller:** `tr` (varsayılan) ve `en`.
- **Seçim sırası:**
  1. `bahceden_locale` çerezi (kullanıcının açık seçimi);
  2. tarayıcının `Accept-Language` başlığı (ağırlıklara göre, alt etiket yok sayılır: `en-GB` → `en`);
  3. Türkçe.
- **Tek kaynak:** dil seçen tek fonksiyon `web/src/i18n/locale.ts`'dir. next-intl istek yapılandırması ve testler onu kullanır.
- **Dil menüsü** (`LocaleMenu`) başlıkta, tema menüsünün yanında tek ikondur.
  - Seçenekler kendi dilinde yazılır: "Türkçe", "English".
  - Seçim çereze yazılır ve sunucu bileşenleri yenilenir (`router.refresh()`). `<html lang>` aynı istekte doğru dille gelir.
- **Başlık genişliği:** 375px başlığa dördüncü öğe sığsın diye 420px altında marka yazısı ekran okuyucuya bırakılır (`sr-only`), logo görünür kalır.
- **`api`'ye iletim:** `web` sunucu tarafında her `api` çağrısına seçili dili `Accept-Language` olarak ekler (`lib/apiClient.ts`, tek yer). `api` tarayıcının başlığını değil `web`'in seçtiği dili görür; iki taraf aynı dili konuşur.

**Gerekçe:** Öneksiz URL'de dilin tek kalıcı yeri çerezdir; çerez yoksa tarayıcı dili en iyi tahmindir. Dili tek yerde seçip `api`'ye iletmek, sayfa İngilizce iken hata mesajının Türkçe gelmesini önler.

**Alternatifler:** Yalnızca `Accept-Language` (kullanıcı tarayıcı dilinden farklı bir dil seçemez); `localStorage` (sunucu dili bilmez, ilk boyama yanlış dilde gelir); dilin hesapta saklanıp oradan okunması (oturumsuz ziyaretçiye çalışmaz).

### D3 · `api`: metinler `MessageSource` + `Accept-Language` ile çözülür; kodlar sabit

- **Çözümleyici:** `AcceptHeaderLocaleResolver`, desteklenen diller `tr` ve `en`, varsayılan `tr`.
- **Mesaj dosyaları:** `messages.properties` (Türkçe, temel) ve `messages_en.properties`. `spring.messages.fallback-to-system-locale: false`: sunucunun işletim sistemi dili sonucu etkilemez.
- **`ApiException`:** hazır metin değil, **mesaj anahtarı** taşır. Metin `GlobalExceptionHandler` içinde isteğin diliyle çözülür.
- **Bean Validation:** mesajları `{validation.email.required}` biçiminde anahtardır; Spring Boot doğrulayıcısı `MessageSource`'u kullanır.
- **Filtre katmanı:** `ApiAuthenticationEntryPoint` `DispatcherServlet`'ten önce çalışır. Dili çözümleyiciden kendisi alır.
- **Sözleşme:** hata `code` değerleri değişmez; istemci mantığı koda bağlıdır, metin yalnızca gösterilir.

**Gerekçe:** Spring çekirdeği (yeni paket yok); otorite `api` olduğu için doğrulama mesajları da onun dilinde gelir (AGENTS.md §12.3: sunucu mesajı olduğu gibi gösterilir).

**Alternatifler:** `api` Türkçe kalır, `web` hata kodundan kendi metnini üretir (alan bazlı doğrulama mesajları kodla ifade edilemiyor; iki yerde metin tutulur); dil sorgu parametresiyle (`?lang=en`, HTTP standardı değil).

### D4 · Hesabın dili saklanır; kayıtta yazılır, girişte güncellenir

- `accounts.locale` (`VARCHAR(8) NOT NULL DEFAULT 'tr'`, `CHECK (locale IN ('tr','en'))`), changeset `005-account-locale.sql`. Mevcut hesaplar `tr` olur.
- **Yazıldığı ve güncellendiği an:**
  - kayıtta isteğin dili yazılır;
  - her başarılı parola adımında (`POST /sessions`) isteğin diliyle güncellenir: kullanıcının en son kullandığı dil.
- **Hesabın dilinde gönderilenler:** doğrulama bağlantısı, giriş kodu ve "hesabınız zaten var" bildirimi.
  - "Zaten var" bildirimi kayıt isteğinin değil **mevcut hesabın** dilindedir: e-posta adresin sahibine gider, isteği yapan kişi başkası olabilir.

**Gerekçe:** E-posta istek-yanıt döngüsü dışında okunur. Yeniden gönderme gibi akışlarda isteğin dili hesabın sahibinin dili olmayabilir.

**Alternatifler:**
- Hesapta saklamamak, isteğin dilini kullanmak: "zaten var" bildirimi yabancının dilinde gider.
- Ayrı bir "dil tercihini değiştir" ucu: bugün ekranı yok; girişte güncelleme ihtiyacı karşılıyor.

## KVKK ve yetkilendirme

- **KVKK:** Dil tercihi tek başına kişiyi tanımlamaz ama hesaba bağlı bir tercihtir. Hesapla birlikte yaşar ve silinir; log'a yazılmaz. Çerez oturum bilgisi taşımaz.
- **Yetkilendirme:** etkisi yok. Yeni uç yok; `accounts.locale` yalnızca hesabın kendi akışlarında okunur.

## Risks / Trade-offs

- **Anahtar eksikliği:** anahtar kümesi eşitliği testle zorunlu; tip güvenliği yalnızca `tr.json` üzerinden.
- **Hesap dili gecikmesi:** oturum açıkken dil değiştirmek hesabın dilini bir sonraki girişe kadar değiştirmez.
- **Başlık:** 420px altında marka yazısı görünmez.

## Migration Plan

`005-account-locale.sql` ekleyici bir değişikliktir. Varsayılan değer mevcut satırları doldurur; `--rollback` kolonu düşürür. `web` tarafında URL değişmediği için yönlendirme gerekmez.

## Open Questions

Yok. Yeni bir dil eklemek yalnızca bir `messages/<dil>.json`, bir `messages_<dil>.properties` ve desteklenen dil listesi değişikliğidir.

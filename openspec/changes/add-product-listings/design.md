# Design: add-product-listings

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-047, D2 → ADR-048, D3 → ADR-049, D4 → ADR-050, D5 → ADR-051, D6 → ADR-052, D7 → ADR-053.
> Çerçeve kararlar zaten alındı: [ADR-038](../../../docs/adr/038-listings-publish-immediately-with-admin-takedown.md) (ilan ön onaysız yayına girer, admin gerekçeyle kaldırır, satıcı kaldırılan ilanı kendisi açamaz), [ADR-039](../../../docs/adr/039-uploads-on-local-volume-served-by-api.md) (yerel volume, `api` sunar, ImageIO ile boyutlandırma ve EXIF temizliği), [ADR-044](../../../docs/adr/044-seller-role-follows-status-with-query-gate.md) (onaylı satıcı kapısı), [ADR-007](../../../docs/adr/007-query-level-ownership-scoping.md) (sorgu seviyesinde sahiplik). Aşağıdaki kararlar bunlara aykırı değildir.

## Kararlar

### D1 · İlan durumları: mevcut üç duruma `REMOVED` eklenir; geçişler adlandırılmış ve tek yerde

`products.status` bugün `DRAFT`, `PUBLISHED`, `ARCHIVED` değerlerini alıyor. Bu değerlere `REMOVED` eklenir: admin tarafından kaldırılmış ilan. Ayrıca `status_reason` (kaldırma gerekçesi) ve `status_changed_at` kolonları eklenir.

İzinli geçişler `ProductTransition` enum'unda, satıcı kaydındaki `SellerTransition` deseniyle tek yerde tutulur. Her geçiş eylemle adlandırılır; bir geçişin birden fazla başlangıç durumu olabilir:

| Geçiş | Kim | Başlangıç → hedef | Koşul |
|---|---|---|---|
| `PUBLISH` | Satıcı | `DRAFT` → `PUBLISHED` | ≥ 1 fotoğraf |
| `UNPUBLISH` | Satıcı | `PUBLISHED` → `DRAFT` | - |
| `ARCHIVE` | Satıcı | `DRAFT`, `PUBLISHED` → `ARCHIVED` | - |
| `RESTORE` | Satıcı | `ARCHIVED` → `DRAFT` | - |
| `REMOVE` | Admin | `DRAFT`, `PUBLISHED` → `REMOVED` | gerekçe |
| `REINSTATE` | Admin | `REMOVED` → `PUBLISHED` | ≥ 1 fotoğraf |

Her geçiş `product_status_changes` tablosuna yazılır. Bu tablo yalnızca eklenerek büyür ve repository'si çıplak bir Spring Data `Repository`'dir ([ADR-040](../../../docs/adr/040-seller-application-is-the-seller-record.md) ile aynı desen).

Satıcı `REMOVED` ilanı düzenleyebilir, fotoğrafını değiştirebilir, ama geçiş yapamaz. Bu durumdan çıkışı yalnızca admin yapar (ADR-038 §3).

İlk yayında `published_at` yazılır. Yayından çekip yeniden yayına almak bu tarihi **değiştirmez**. Böylece ilanı aç-kapa yaparak vitrinde öne çıkarmak mümkün olmaz.

**Gerekçe:**
- Kaldırma, satıcının kendi taslak ve arşiv kavramlarından farklı bir durum. Ayrı bir değer, "satıcı açamaz" kuralını tek bir koşula indirger.
- Adlandırılmış geçişler, kimin hangi geçişi yapabileceğini ve koşulları (fotoğraf, gerekçe) tek bir tabloda sabitler.

**Reddedilen alternatifler:**
- (a) Ayrı bir `removed_by_admin` bayrağı: durum ile bayrak geçersiz kombinasyonlar üretir (örneğin `ARCHIVED` ve kaldırılmış).
- (b) Kaldırılan ilanı `ARCHIVED`'a çekmek: satıcı arşivden geri alıp yeniden yayına alabilir; ADR-038 §3 delinir.
- (c) Yeniden yayına almada `published_at`'i güncellemek: vitrinde öne çıkmak için aç-kapa yapmayı ödüllendirir.

### D2 · Satıcı uçları `/api/v1/sellers/me/products` altında; her istek önce onaylı satıcı kapısından, sonra sahiplik sorgusundan geçer

- `GET/POST /sellers/me/products`
- `GET/PUT /sellers/me/products/{id}`
- `POST /sellers/me/products/{id}/{publish|unpublish|archive|restore}`
- fotoğraf uçları (D3)

Her serviste ilk satır `requireApprovedSeller(accountId)` çağrısıdır ([ADR-044](../../../docs/adr/044-seller-role-follows-status-with-query-gate.md)). İlan bu satıcının kimliğiyle `findByIdAndSellerId` sorgusundan okunur; başka satıcının ilanı **404** döner.

Uçlar `ROLE_SELLER` rolünü de ister. Yine de otorite durum kapısıdır: askıya alınmış bir satıcının token'ında rol henüz duruyorsa bile istek **403** `SELLER_NOT_APPROVED` alır.

`slug` oluşturmada ilan adından üretilir (Türkçe karakterler ASCII'ye çevrilir, çakışmada `-2`, `-3`) ve sonra **değişmez**. Satıcı adı değiştirse bile ürün adresi kırılmaz. `SellerService.slugify` yardımcısı ortak bir yere taşınır ve tekrar yazılmaz (K9).

**Gerekçe:**
- `/sellers/me` deseni mevcut: kayıt token'dan bulunur, istemci satıcı kimliği göndermez.
- Rol ve durum kapısı birlikte, ADR-044'te tanımlanan iki katmanı uygular.

**Reddedilen alternatifler:**
- (a) `/api/v1/products` altında `sellerId` parametresiyle yazma: istemciden gelen kimliğe güvenmek demek; vitrin ucuyla karışır.
- (b) Yalnızca rol denetimi: askıdaki satıcıya 15 dakikalık bir pencere açar (ADR-044'ün reddettiği durum).
- (c) Ad değişince `slug`'ı güncellemek: paylaşılmış ürün bağlantıları kırılır.

### D3 · Fotoğraflar ayrı bir tabloda, tek tek yüklenir; sıra açık bir pozisyon kolonuyla tutulur

`product_photos` tablosundaki her satırda şunlar bulunur:

- `product_id`, `position` (0–4);
- `card_key` ve `large_key`: iki boyutun depolama anahtarları;
- `width`, `height`: büyük boyutun ölçüleri; `next/image` için gerekir;
- zaman damgaları.

`(product_id, position)` tekildir. Kapak, `position = 0` olan satırdır.

Uçlar:

- `POST /sellers/me/products/{id}/photos`: multipart, tek dosya; fotoğraf sona eklenir.
- `DELETE /sellers/me/products/{id}/photos/{photoId}`: kalan fotoğraflar sıkıştırılır (0..n-1).
- `PUT /sellers/me/products/{id}/photos/order`: `{"photoIds": [...]}`. Liste ilanın fotoğraflarını eksiksiz ve tekrarsız içermek zorundadır; aksi halde 400.

Sınırlar:

- İlan başına en fazla 5 fotoğraf: `PRODUCT_PHOTO_LIMIT_REACHED` (409).
- Yayındaki veya kaldırılmış bir ilanın son fotoğrafı silinemez: `PRODUCT_PHOTO_REQUIRED` (409).

Hız sınırı: `PRODUCT_PHOTO_UPLOAD`, hesap başına saatte 60.

**Gerekçe:**
- Satıcı sahada telefonla tek tek fotoğraf çeker. Taslak, ilan bilgileri girildiği anda vardır, yani belgede olduğu gibi yetim kayıt sorunu yoktur ([ADR-042](../../../docs/adr/042-seller-application-single-multipart-request.md)'nin aksine). Bu yüzden fotoğraf başına istek, mobil bağlantıda tek bir dev istekten daha dayanıklıdır.
- Açık bir pozisyon kolonu sıralamayı ve kapağı tek bir alana indirger. Ayrı bir kapak alanı tutarsızlık doğurur.

**Reddedilen alternatifler:**
- (a) İlan ve bütün fotoğrafların tek multipart istekte gelmesi: 5 × 10 MB tek istekte gider; tek fotoğraf hatasında hepsi yeniden yüklenir.
- (b) Fotoğrafları `seller_documents` benzeri genel bir tabloda tutmak: herkese açık ve gizli dosyalar karışır ([ADR-045](../../../docs/adr/045-seller-documents-table-content-sniffing-protected-serving.md)'in reddettiği durum).
- (c) Ayrı bir `cover_photo_id` alanı: pozisyonla çelişebilir, iki kaynaklı gerçek oluşur.

### D4 · Görüntü işleme: piksel sınırı, EXIF yönü, iki boyut, JPEG yeniden kodlama

Bütün işlem `catalog/PhotoProcessor`'da yapılır; yalnızca JDK kullanılır (`javax.imageio`, `java.awt`).

1. **Tür:** dosyanın ilk baytlarına bakılır (`FileType`); JPEG ve PNG kabul edilir. PDF burada reddedilir.
2. **Boyut:** dosya sınırı 10 MB (`bahceden.storage.max-photo-bytes`).
3. **Piksel sınırı (görüntü bombası koruması):** görüntü belleğe açılmadan önce `ImageReader` ile yalnızca başlıktan genişlik ve yükseklik okunur. 40 megapikselden büyük görüntü reddedilir.
4. **EXIF yönü:** JPEG'in APP1 (Exif) bölümünden yön etiketi (`0x0112`) küçük bir okuyucuyla elle okunur ve görüntü buna göre döndürülür ya da çevrilir. Etiket yoksa veya okunamazsa görüntü olduğu gibi kullanılır.
5. **Boyutlandırma:** uzun kenarı 400 px (kart) ve 1600 px (büyük) olacak şekilde iki çıktı üretilir. Orijinal daha küçükse büyütülmez. Ölçekleme `Graphics2D` ile, bikübik ara değerlemeyle yapılır.
6. **Kodlama:** iki çıktı da JPEG, kalite 0,85. PNG'nin saydam alanı beyaz zemine düzleştirilir. Yeniden kodlama bütün meta veriyi (EXIF, konum, ICC dışı bölümler) atar.
7. Orijinal dosya **saklanmaz**. Yalnızca iki çıktı `FileStorage` ile yazılır. Transaction geri alınırsa ikisi de silinir (ADR-042'deki desen).

Okunamayan dosya (bozuk ya da CMYK JPEG gibi ImageIO'nun desteklemediği biçim) `400` döner ve `fieldErrors` içinde `photo` alanıyla gelir.

**Gerekçe:**
- Telefon fotoğrafları yönünü pikselde değil EXIF etiketinde taşır. ImageIO bu etiketi yok saydığı için etiket uygulanmazsa fotoğrafların çoğu yan görünür.
- Orijinali saklamamak disk kullanımını ve kişisel veri riskini (konum) azaltır.
- İki sabit boyut, vitrin kartı ve detay galerisi için yeterli.

**Reddedilen alternatifler:**
- (a) Görüntü işleme kütüphanesi (Thumbnailator, metadata-extractor): K7 dışında; tek bir etiket için gereksiz.
- (b) Orijinali de saklamak: konum gibi meta veri diskte kalır, gelecekteki bir "orijinali indir" ihtiyacı bugün yok.
- (c) Boyutlandırmayı `next/image`'a bırakmak: büyük orijinal diskte ve sunucu belleğinde kalır, EXIF temizlenmez ([ADR-039](../../../docs/adr/039-uploads-on-local-volume-served-by-api.md)'un reddettiği durum).

### D5 · Fotoğraf sunumu: `api`'de anahtarla herkese açık uç, `web`'de akış aktaran bir Route Handler

`api` fotoğrafı `GET /api/v1/product-photos/{key}` adresinden herkese açık sunar. Anahtar, sunucunun ürettiği 128 bitlik rastgele değerdir (`FileStorage`). Yanıt başlıkları:

- `Content-Type: image/jpeg`;
- `Cache-Control: public, max-age=31536000, immutable`;
- `X-Content-Type-Options: nosniff`.

Fotoğraf değiştirildiğinde yeni anahtar üretilir, bu yüzden uzun önbellek güvenlidir. Var olmayan anahtar 404 döner. Anahtar biçimi `FileStorage`'da zaten doğrulanıyor.

**Anahtar `product_photos` tablosunda kayıtlı olmalıdır.** Satıcı belgeleri de aynı depolama dizininde, aynı anahtar biçimiyle durur ([ADR-045](../../../docs/adr/045-seller-documents-table-content-sniffing-protected-serving.md)). Uç yalnızca anahtar biçimine baksaydı, bir belge anahtarını bilen kişi gizli belgeyi herkese açık fotoğraf ucundan indirebilirdi. Bu yüzden uç önce anahtarı `card_key` veya `large_key` olarak arar. Bulamazsa, dosya diskte olsa bile 404 döner. Bu iddia `critical` testle sabitlenir.

> Uygulama notu (2026-10-08): bu kural Faz 2'nin tasarımı sırasında eklendi.

Tarayıcı `api`'ye konuşmaz (`AGENTS.md` §12.2). `web` tarafında `/media/product-photos/[key]` Route Handler'ı `api`'deki fotoğrafı akış olarak ve aynı başlıklarla iletir. Ekranlar `next/image`'ı bu göreli adresle ve `unoptimized` olarak kullanır: görüntü zaten sunucuda boyutlandırılmıştır, ikinci kez işlenmez. `width` ve `height` sözleşmeden gelir, böylece yerleşim sıçramaz.

Sözleşme dosya yolu taşımaz; yalnızca anahtarları taşır: `ProductPhotoView { cardKey, largeKey, width, height }`. Adresi `web` kurar (§3: `api` istemci rotası bilmez).

**Gerekçe:**
- Fotoğraf herkese açık içeriktir ve en çok istenen kaynaktır. Uzun önbellek ve değişmez anahtar, CDN olmadan da yükü azaltır.
- Rastgele anahtar, taslak bir ilanın fotoğrafının sıralı kimlikle taranmasını engeller.

**Reddedilen alternatifler:**
- (a) `next/image` `remotePatterns` ile `api`'nin iç adresini kullanmak: iç ana bilgisayar adı tarayıcının gördüğü adrese sızar ve görüntü iki kez işlenir.
- (b) Fotoğrafı sıralı `photoId` ile sunmak: taslak fotoğraflar sayılarak taranabilir.
- (c) Taslak fotoğrafları da yetkili uçtan sunmak: önbellek ve aktarma iki yola bölünür. Rastgele anahtarla elde edilecek ek güvence düşük.

### D6 · Vitrin: kapak fotoğrafı ikinci sabit sorguyla gelir; detay ucu `slug` ile, yalnızca görünür ürünler

- `ProductSummary`'ye `coverPhoto` (`ProductPhotoView`, boş olabilir) eklenir.
- Liste sorgusu değişmez. Sayfanın ürün kimlikleriyle **tek** bir ek sorgu (`position = 0 and product_id in (…)`) kapakları getirir. Sorgu sayısı ürün sayısından bağımsız kalır: içerik + sayım + kapak, en çok 3. Bu iddia mevcut sorgu sayısı testine eklenir.
- `GET /api/v1/products/{slug}` (`ProductDetail`): ürün, üretici ve sıralı fotoğraflar. Koşullar vitrinle aynıdır (`PUBLISHED` ve satıcı `APPROVED`); aksi halde 404 `PRODUCT_NOT_FOUND`.
- Seed ürünlerinin fotoğrafı yoktur. Kart ve detay sayfası fotoğrafsız ürünü bugünkü tipografik düzenle gösterir; yer tutucu görsel yoktur ([Tasarım sistemi](../../../docs/reference/03-design-system.md) §6).

**Gerekçe:**
- `@OneToMany` fetch join, sayfalama ile birlikte bellekte sayfalamaya (Hibernate uyarısı) yol açar. Ayrı sabit sorgu, sayfalamayı veritabanında tutar.
- Detayın vitrinle aynı koşulu taşıması, listede görünmeyen bir ürünün doğrudan adresle açılmasını engeller ([ADR-041](../../../docs/adr/041-storefront-filters-seller-status-in-query.md) nötr sonucu).

**Reddedilen alternatifler:**
- (a) Fetch join ile fotoğrafları liste sorgusunda getirmek: bellekte sayfalama.
- (b) `products.cover_key` gibi bir alanla kapağı ürün satırına kopyalamak: iki kaynaklı gerçek oluşur.
- (c) Kapağı zorunlu yapmak: seed ürünleri sözleşmeyi ihlal ederdi.

### D7 · Admin ilan yüzeyi: liste ve iki eylem; gerekçe diyaloğu ortak bileşen

`api` uçları `ROLE_ADMIN` ile korunur:

- `GET /api/v1/admin/products?status=` (varsayılan `PUBLISHED`; ürün, üretici ve kapak);
- `POST /admin/products/{id}/remove {reason}`;
- `POST /admin/products/{id}/reinstate`.

Kaldırma ve yeniden açma, satıcının hesabına onun dilinde e-postayla bildirilir. Seed satıcılarının hesabı yoktur; onlar için e-posta adımı atlanır.

`web` tarafında `/admin/products`: "Yayında" ve "Kaldırılan" sekmeleri. Her satırda vitrin bağlantısı ve eylem bulunur. Kaldırma gerekçe diyaloğuyla yapılır. `SellerDecisionActions` içindeki gerekçe diyaloğu `components/ReasonDialog` adlı ortak bileşene çıkarılır ve iki ekran onu kullanır (K9). Başlıktaki "Yönetim" bağlantısı `/admin/sellers`'a gitmeye devam eder; iki admin sayfası arasında sekme gezintisi eklenir.

**Gerekçe:**
- Admin'in işi uygunsuz ilanı hızla bulup kaldırmak. Ayrı bir admin detay sayfası yerine herkese açık ürün sayfası yeterli; admin ilanı alıcının gördüğü gibi görür.
- Gerekçe diyaloğu iki yerde aynı davranır; ikinci kopya yazılmaz.

**Reddedilen alternatifler:**
- (a) Admin için ayrı ilan detay sayfası: vitrin sayfasının kopyası olur. Kaldırılmış ilanı görmek için satıcı panelindeki görünüm zaten var.
- (b) Kaldırmayı yalnızca satıcı askısıyla yapmak: tek bir uygunsuz ilan için bütün satıcıyı cezalandırır.

## Mevcut ADR'lerden türeyen, yeni karar içermeyen detaylar

| Detay | Kaynak |
|---|---|
| Para alanı: istek ve yanıtta minor unit tamsayı + `TRY` | [ADR-006](../../../docs/adr/006-money-as-minor-unit.md) |
| Hata kodları: `PRODUCT_NOT_FOUND` (404), `PRODUCT_INVALID_TRANSITION` (409), `PRODUCT_PHOTO_REQUIRED` (409; fotoğrafsız yayına alma ve son fotoğrafı silme), `PRODUCT_PHOTO_LIMIT_REACHED` (409), dosya hataları `VALIDATION_FAILED` + `fieldErrors[photo]` | §11.5, [ADR-031](../../../docs/adr/031-api-messages-by-accept-language.md) |
| Sayfa boyutu üst sınırı 50, ortak `PageResponse` | [ADR-012](../../../docs/adr/012-page-size-capped-on-server.md) |
| Geçmiş tablosu yalnızca eklenir | [ADR-040](../../../docs/adr/040-seller-application-is-the-seller-record.md) |
| Dosya-transaction tutarlılığı (geri almada dosyayı sil) | [ADR-042](../../../docs/adr/042-seller-application-single-multipart-request.md) |
| BFF multipart iletimi (`handleBffFormMutation`), köken denetimi | [ADR-023](../../../docs/adr/023-web-bff-cookies-and-header-csrf.md), ADR-042 |
| E-posta hesabın dilinde, gönderim hatası akışı kesmez | [ADR-032](../../../docs/adr/032-account-locale-stored-and-refreshed-on-login.md), [ADR-019](../../../docs/adr/019-email-via-smtp-provider-agnostic.md) |

**Bu change'te alınan küçük varsayımlar** (karar düzeyinde değil; review'da değiştirilebilir):

- **Alan sınırları:**
  - ad 2–200 karakter;
  - açıklama en çok 2000 karakter, düz metin;
  - fiyat 1 kuruş ile 10.000.000 kuruş (100.000 TL) arası;
  - stok 0–100.000.
- **Stok sıfır olabilir:** yayındaki ilan "stokta yok" olarak görünür (bugünkü kart davranışı).
- **Fiyat girdisi:** satıcı fiyatı TL olarak (örn. `24,50`) yazar. `web` bunu `money.ts` içinde kuruşa çevirir; bu bir hesap değil, girdi ayrıştırmadır. Otorite yine `api`'dir ve tamsayı kuruş alır.
- **Para birimi:** her zaman `TRY`; satıcı seçmez.
- **Satıcı paneli listesi:** durumlarına göre rozetli tek bir liste, sayfa başına 20; kendi durum süzgeci yok.
- **Dev seed:** satıcı kaydı yoksa `uretici@ornek.test` için onaylı bir satıcı kaydı oluşturur (`on conflict` ile). Fotoğraflar elle yüklenir.

## Yetkilendirme ve kişisel veri

| Uç | Kim | Denetim |
|---|---|---|
| `/api/v1/sellers/me/products/**` | Onaylı satıcı | `hasRole("SELLER")` + `requireApprovedSeller` + `findByIdAndSellerId` |
| `GET /api/v1/products`, `GET /api/v1/products/{slug}` | Herkes | Sorgu: `PUBLISHED` ve satıcı `APPROVED` |
| `GET /api/v1/product-photos/{key}` | Herkes | Rastgele anahtar; anahtar biçimi doğrulanır |
| `/api/v1/admin/products/**` | `ROLE_ADMIN` | `hasRole("ADMIN")` |

Yeni kişisel veri yok. Fotoğrafların meta verisi (konum) yeniden kodlamayla atılır.

## Risks / Trade-offs

| Konu | Değerlendirme |
|---|---|
| ImageIO'nun JPEG çözücüsü bazı dosyaları okuyamaz (CMYK, bozuk) | Alan hatası ve "başka bir fotoğraf deneyin" mesajı. Ölçüm gösterirse kütüphane bir K7 kararıdır |
| Görüntü işleme CPU kullanır (40 MP'ye kadar) | Hız sınırı saatte 60. Tek sunucuda eşzamanlı yükleme az. Ölçüm gelince sınırlar ayarlanır |
| Fotoğraf uçları herkese açık | Rastgele 128 bit anahtar. Taslak fotoğrafın anahtarı yalnızca sahibine verilir |
| Seed ürünleri fotoğrafsız | Kapak alanı boş olabilir; ekranlar bunu tasarlanmış durum olarak gösterir |
| Kaldırılan ilanın fotoğrafları önbellekte kalabilir | İlan vitrinden ve detaydan düşer; fotoğraf anahtarı başka yerde listelenmez. Önbellekteki kopya, anahtarı bilen biri için görünür kalabilir |

## Migration Plan

1. `009-product-listings.sql`:
   - `products_status_check` kısıtı düşürülür ve `REMOVED`'ı da içerecek şekilde yeniden eklenir.
   - Yeni kolonlar: `status_reason varchar(500)`, `status_changed_at timestamptz`.
   - Yeni tablolar: `product_photos` (`(product_id, position)` tekil; anahtarlar tekil; `position between 0 and 4`) ve `product_status_changes`.
   - İndeks: `products (seller_id, updated_at desc, id desc)`, satıcı paneli listesi için.
   - `--rollback` ters sırayla tabloları düşürür ve kısıtı eski değerlerle geri koyar. `REMOVED` satırı varsa geri alma kısıtta düşer; bu belgelenir.
2. `010-dev-seed-product-listings.sql` (`contextFilter:dev`): `uretici@ornek.test` için onaylı satıcı kaydı (`on conflict (account_id) do nothing`) ve `ROLE_SELLER`.
3. `application.yaml`: `bahceden.storage.max-photo-bytes: 10485760`. Multipart sınırı zaten 10 MB.
4. Uygulanmış changeset'lere dokunulmaz.

## Open Questions

Bu change'i bloke etmeyen sorular:

- Satıcının mağaza (profil) sayfası ve satıcı adına göre süzme: vitrin arama ve filtre işiyle birlikte ele alınacak.
- Fotoğraf işleme süresi ölçülecek; ihtiyaç doğarsa arka plan işlemesi ayrı bir karar (bugün kuyruk §3'e aykırı).

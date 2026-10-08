# add-product-listings

**Durum:** Onaylandı (2026-10-08, proje sahibi `/opsx:apply`), uygulandı, PR #2 ile `main`'e merge edildi ve kullanıcı onayıyla archive edildi (2026-10-08).

> Bu change [ADR-038](../../../../docs/adr/038-listings-publish-immediately-with-admin-takedown.md) (ilan ön onaysız yayına girer, admin kaldırabilir) ve [ADR-039](../../../../docs/adr/039-uploads-on-local-volume-served-by-api.md) (yerel volume, ImageIO ile boyutlandırma) üzerine kurulur. Onaylı satıcı kapısı ([ADR-044](../../../../docs/adr/044-seller-role-follows-status-with-query-gate.md)) ve `FileStorage` ([ADR-045](../../../../docs/adr/045-seller-documents-table-content-sniffing-protected-serving.md)) `add-seller-onboarding` ile hazırlandı.

## Why

Onaylı bir satıcı bugün hiçbir şey satamıyor: ürün ekleyecek bir ekran ya da uç yok. Vitrindeki ürünlerin hepsi dev seed'inden geliyor ve hiçbirinin fotoğrafı yok. Pazaryerinin vaadi, üreticinin ürününü kolayca listelemesi. Satıcı için en kritik akış ürün eklemek (`AGENTS.md` §12.5).

Alıcı tarafında da ürünün tek bir sayfası yok: liste kartı dışında ürünü ayrıntılı görecek, fotoğraflarına bakacak bir yer bulunmuyor.

## What Changes

- **`api`, satıcı ilanları (`/api/v1/sellers/me/products`):**
  - Onaylı satıcı ilan oluşturur (taslak), düzenler, listeler.
  - İlanı yayına alır, yayından çeker, arşivler ve arşivden geri alır.
  - Her işlem önce `requireApprovedSeller` kapısından geçer, sonra sahiplik sorgu seviyesinde denetlenir (`findByIdAndSellerId`). Başka satıcının ilanı 404 döner.
- **`api`, fotoğraflar:**
  - Bir ilanda 1–5 fotoğraf olur; ilk fotoğraf kapaktır.
  - Satıcı fotoğrafları tek tek yükler, siler ve sıralar.
  - Yüklenen fotoğraf sunucuda okunur. EXIF yön etiketi uygulanır, ardından iki boyutta (kart ve büyük) JPEG olarak yeniden kodlanır. Böylece konum dahil bütün meta veri atılır.
  - Fotoğraf sunucunun ürettiği anahtarla herkese açık bir uçtan, uzun önbellek başlığıyla sunulur.
  - Yayına almak için en az bir fotoğraf gerekir.
- **`api`, vitrin:**
  - Liste kartı kapak fotoğrafını taşır. Fotoğrafı olmayan eski seed ürünlerinde kapak alanı boş döner.
  - Yeni herkese açık uç: `GET /api/v1/products/{slug}` (ürün detayı). Yalnızca yayında olan ve satıcısı onaylı ürün döner; diğerleri 404.
- **`api`, admin kaldırma (ADR-038 §3):**
  - `/api/v1/admin/products` altında ilan listesi, gerekçeyle kaldırma ve yeniden açma.
  - Kaldırılan ilanı satıcı kendisi yayına alamaz. Satıcı gerekçeyi görür, ilanı düzeltebilir; admin yeniden açar.
  - Kaldırma ve yeniden açma satıcıya hesabının dilinde e-postayla bildirilir.
- **`api`, geçmiş ve şema:**
  - Her durum geçişi silinmeyen bir geçmiş tablosuna yazılır (satıcı kaydıyla aynı desen).
  - Yeni durum: `REMOVED`.
- **`web`, satıcı paneli (`(seller)` rota grubu):**
  - `/seller/products`: kendi ilanlarım.
  - `/seller/products/new`: taslak oluşturma.
  - `/seller/products/[id]`: bilgiler, fotoğraflar (yükle, sırala, sil), yayın eylemleri, admin kaldırdıysa gerekçe.
  - "Hesabım"daki satıcılık kartına ve başlığa "Ürünlerim" bağlantısı.
- **`web`, vitrin:**
  - Ürün kartında kapak fotoğrafı ve detay bağlantısı.
  - Yeni `/products/[slug]` detay sayfası: galeri, açıklama, fiyat, stok, üretici.
- **`web`, admin:** `/admin/products` (yayında / kaldırılan sekmeleri, kaldır / yeniden aç). Gerekçe diyaloğu satıcı ekranlarıyla paylaşılan bir bileşene çıkarılır.
- **`web`, görsel aktarma:** `/media/product-photos/[key]` Route Handler'ı, `api`'deki fotoğrafı tarayıcıya akış olarak ve önbellek başlıklarıyla iletir.
- **Sözleşme:** yeni uçlar ve şemalar; `ProductSummary`'ye isteğe bağlı `coverPhoto` alanı eklenir. Kırıcı değişiklik yok.

## Capabilities

- **Yeni:** `product-listings` (delta: `specs/product-listings/spec.md`). Satıcının ilan yönetimi, fotoğraflar, yayın durumları, admin kaldırma, geçmiş.
- **Değişen:** `storefront-catalog` (delta: `specs/storefront-catalog/spec.md`).
  - Liste gereksinimi değişir (MODIFIED): kapak fotoğrafı, `REMOVED` gizlenir.
  - Yeni "Ürün detayı" gereksinimi (ADDED).

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `catalog` paketi: `Product` (yeni alanlar ve geçişler), `ProductTransition`, `ProductPhoto`, `ProductStatusChange`, `ListingService` (satıcı), `ProductService` (vitrin detayı ve kapak), `ProductModerationService` (admin), `PhotoProcessor` (ImageIO, EXIF yönü), controller'lar, DTO'lar, mapper. `common/storage` değişmez, yeniden kullanılır. `SecurityConfig`, `RateLimitPolicy.PRODUCT_PHOTO_UPLOAD`, mesajlar, testler |
| `web/` | Evet | `(seller)/seller/products/*`, `(shop)/products/[slug]`, `(admin)/admin/products`, `app/media/product-photos/[key]`, `features/listings`, `ProductList` (kapak), ortak `ReasonDialog`, `money.ts` (fiyat girdisini kuruşa çevirme), mesajlar, testler |
| DB | Evet | `009-product-listings.sql`: `products` tablosuna `REMOVED` durumu, `status_reason`, `status_changed_at`; yeni `product_photos` ve `product_status_changes` tabloları; indeksler. `010-dev-seed-product-listings.sql` (dev): `uretici@ornek.test` için hesaplı, onaylı satıcı. Ekleyici; mevcut veri değişmez |
| `shared/` | Evet (ekleyici) | Yeni uçlar ve şemalar; `ProductSummary.coverPhoto` (boş olabilir) |
| `docs/` | Evet | D1–D7 → ADR-047…053; `docs/product/urun-ilanlari.md`; feature dosyası `docs/features/urun-ilanlari.md`; `vitrin-ve-arama.md` Tarihçe; `01-domain-model.md`, `02-api-reference.md`, `03-design-system.md`; `00-current-state.md` |

## Bağımlılık

Yeni paket yok (K7). Görüntü işleme `javax.imageio` ve `java.awt` ile (ADR-039), EXIF yön etiketi küçük bir JPEG APP1 okuyucusuyla elle okunur. `web` tarafında `next/image` (Next.js çekirdeği) kullanılır.

## Kişisel veri (K10)

- Yeni kişisel veri alanı yok.
- Fotoğraflar yeniden kodlanırken bütün meta veri atılır; telefonun yazdığı konum bilgisi de buna dahil.
- Kaldırma gerekçesi satıcıya gösterilir ve e-postada geçer.
- Testlerde sahte, programla üretilmiş görseller kullanılır; gerçek fotoğraf yok.

## Non-goals

- Kategori, etiket, arama ve filtre (vitrin capability'sinin açık konusu).
- Varyant (aynı ürünün farklı boy veya paketleri) ve toplu ilan yükleme.
- Sepet, sipariş, stok düşme (KALANLAR A1).
- Kargo bilgisi ve gönderim süresi alanları (KALANLAR A2).
- Alıcının ilan şikâyet etmesi (ADR-038 nötr sonucu).
- WebP/HEIC giriş veya çıkış biçimi (ADR-039). Çıkış her zaman JPEG.
- CDN ve görsel önbelleğinin ayrı bir katmanda tutulması (ADR-039).
- Seed ürünlerine fotoğraf eklemek. Liquibase volume'a dosya yazamaz; seed ürünleri fotoğrafsız kalır.
- Satıcının mağaza (profil) sayfası.

## Riskler

- **Görüntü bombası:** çok büyük piksel boyutlu bir dosya belleği tüketebilir. Görüntü okunmadan önce boyutları başlıktan okunur ve üst sınır aşılırsa reddedilir (D4).
- **ImageIO'nun JPEG desteği sınırlı:** CMYK JPEG gibi az kullanılan biçimler okunamayabilir. Okunamayan dosya alan hatasıyla reddedilir ve kullanıcıya başka bir dosya denemesi söylenir.
- **Fotoğraf uçları herkese açık:** anahtarı bilen, taslak ilanın fotoğrafını da görebilir. Anahtar 128 bit rastgele olduğu için tahmin edilemez; taslak fotoğrafı yalnızca sahibine verilir (D5).
- **Diskteki dosyalar:** silinen fotoğrafların dosyaları diskten de silinir; silme başarısızsa yetim dosya kalır ve loglanır. Volume yedeklenmiyor (L22).

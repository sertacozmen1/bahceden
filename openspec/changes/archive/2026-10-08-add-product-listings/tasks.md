# Tasks: add-product-listings

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema değişikliği ekleyicidir: yeni kolonlar ve tablolar; durum kısıtı genişler.

## Faz 0 · Ürün belgesi

- [x] 0.1 `docs/product/urun-ilanlari.md` (şablondan):
  - kullanıcı problemi (sahada telefonla ürün ekleme);
  - rakip analizi;
  - kabul kriterleri (spec senaryolarıyla eşleşir);
  - başarı metrikleri (onaydan ilk yayına geçen süre, fotoğraflı ilan oranı).

  `docs/product/index.md` tablosu, mkdocs nav.

## Faz 1 · Satıcı taslak ilanları: `api` → sözleşme → `web`

Bu faz bittiğinde onaylı satıcı taslak ilan oluşturur, düzenler ve listeler. Yayın ve fotoğraf yoktur; vitrin değişmez.

- [x] 1.1 `009-product-listings.sql` (design.md Migration Plan 1; `--rollback` dahil) ve master changelog.
- [x] 1.2 Domain ve repository'ler:
  - `ProductStatus.REMOVED`, `ProductTransition` (D1 tablosu), `Product` için oluşturma, güncelleme ve geçiş metotları;
  - `ProductStatusChange` + yalnızca ekleme yapan repository;
  - `ProductRepository`: `findByIdAndSellerId`, satıcının sayfalı listesi, `existsBySlug`.
  - `slugify`'ı ortak bir yardımcıya taşı ve `SellerService` ile paylaş (K9).
- [x] 1.3 `ListingService`: `create`, `update`, `listOwn`, `getOwn`. Her biri önce `requireApprovedSeller`, sonra sahiplik sorgusu (D2). Oluşturma geçmişe `null → DRAFT` olarak yazılır.
- [x] 1.4 DTO'lar (`@Schema` açıklama ve örnek, Bean Validation; varsayımlardaki sınırlar): `ListingRequest`, `SellerListingView` (durum, gerekçe, fotoğraflar). `SellerListingController` (`/api/v1/sellers/me/products`). `SecurityConfig` (`hasRole("SELLER")`). Yeni `ApiErrorCode` değerleri. Mesajlar (tr, en).
- [x] 1.5 `010-dev-seed-product-listings.sql` ve `DevSeedIsolationTest`.
- [x] 1.6 `openapi.yaml` yeniden üretilir.
- [x] 1.7 **Test (`critical`):**
  - oluşturma `DRAFT` açar ve vitrinde görünmez;
  - kaydı olmayan, bekleyen ve askıdaki satıcı 403 (askıdaki satıcıda rol token'da duruyor olsa bile);
  - başkasının ilanı 404 (görme ve düzenleme);
  - alan doğrulamaları;
  - `slug` üretimi, çakışma ve ad değişince sabit kalması;
  - liste sorgu sayısı;
  - geçmiş kaydı.
- [x] 1.8 **Doğrulama:** `./mvnw -B verify` temiz.
- [x] 1.9 `gen:api`; `features/listings/api.ts`; `money.ts`'e TL girdisini kuruşa çeviren `parseMinorUnits` (testli); BFF Route Handler'ları (`/api/seller/products`, `/api/seller/products/[id]`).
- [x] 1.10 Sayfalar ve durumlar:
  - `(seller)/seller/products` (liste, boş durum, `loading`/`error`); kapı `requireRole("ROLE_SELLER")`;
  - `(seller)/seller/products/new`;
  - `(seller)/seller/products/[id]` (bilgiler formu).
  - Başlık ve "Hesabım" satıcılık kartında "Ürünlerim" bağlantısı. Metinler (tr, en).
- [x] 1.11 **Test (web):** form doğrulaması, sunucu `fieldErrors` eşlemesi, başarı yolu; fiyat ayrıştırma; rolsüz kullanıcıya "bulunamadı"; Route Handler köken denetimi.
- [x] 1.12 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 2 · Fotoğraflar, yayın ve vitrin: `api` → sözleşme → `web`

Bu faz bittiğinde satıcı fotoğraf ekleyip ilanı yayına alır; vitrin kartı kapak fotoğrafını, detay sayfası galeriyi gösterir.

- [x] 2.1 `PhotoProcessor` (D4): tür, dosya boyutu, başlıktan piksel sınırı, EXIF yönü okuyucusu (JPEG APP1, etiket `0x0112`, yön 1–8), iki boyut, JPEG kalite 0,85, saydamlığın beyaza düzleştirilmesi. `BahcedenProperties.storage.maxPhotoBytes`.
- [x] 2.2 `ProductPhoto` + repository. `ListingService.addPhoto`, `deletePhoto`, `reorderPhotos` (D3): 5 sınırı, eksiksiz ve tekrarsız sıralama, son fotoğraf kuralı, dosya-transaction tutarlılığı. `publish`, `unpublish`, `archive`, `restore` (D1; yayın için fotoğraf koşulu; `published_at` yalnızca ilk yayında). `RateLimitPolicy.PRODUCT_PHOTO_UPLOAD`.
- [x] 2.3 `ProductPhotoController`: `GET /api/v1/product-photos/{key}`, önbellek başlıkları (D5), herkese açık. Fotoğraf ve geçiş uçları `SellerListingController`'a.
- [x] 2.4 Vitrin (D6):
  - `ProductSummary.coverPhoto`; kapakları getiren tek ek sorgu;
  - `GET /api/v1/products/{slug}` (`ProductDetail`), vitrinle aynı görünürlük koşulu;
  - `SecurityConfig`'te iki herkese açık uç.
- [x] 2.5 `openapi.yaml` yeniden üretilir; mesajlar.
- [x] 2.6 **Test (`critical`):**
  - EXIF yönü: test içinde programla üretilen, yön etiketli JPEG; döndürülmüş boyutlar;
  - çıktıda EXIF yok;
  - PNG saydamlığı;
  - piksel sınırı;
  - içeriği görüntü olmayan dosya;
  - 6. fotoğraf 409;
  - sıralama doğrulaması ve kapak değişimi;
  - silmede sıkıştırma ve diskten silme;
  - son fotoğraf kuralı;
  - fotoğrafsız yayına alma 409;
  - geçiş tablosu (geçersiz geçiş 409);
  - `published_at` korunuyor;
  - başkasının ilanına fotoğraf 404;
  - fotoğraf ucunun önbellek başlıkları ve var olmayan anahtar 404;
  - satıcı belgesinin anahtarı fotoğraf ucundan 404 (dosya diskte olsa bile; D5);
  - vitrin kapağı ve sorgu sayısı (≤ 3);
  - detay: görünmeyen ürün 404 (taslak, arşiv, askıdaki satıcı);
  - hız sınırı.
- [x] 2.7 **Doğrulama:** `./mvnw -B verify` temiz.
- [x] 2.8 `gen:api`. `app/media/product-photos/[key]` Route Handler (akış, başlıklar). Fotoğraf BFF'i (`/api/seller/products/[id]/photos`, `…/photos/[photoId]`, `…/photos/order`) ve geçiş BFF'i (`/api/seller/products/[id]/[action]`, beyaz liste).
- [x] 2.9 İlan düzenleme ekranına fotoğraf yöneticisi: yükleme; yukarı ve aşağı düğmeleriyle sıralama (sürükle-bırak yok; klavye ve dokunmayla erişilebilir); silme; kapak etiketi; istemcide erken tür ve boyut uyarısı. Yayın eylemleri: yayına al (birincil), yayından çek, arşivle, geri al. `REMOVED` durumunda gerekçe ve "yayına alamazsınız" açıklaması.
- [x] 2.10 Vitrin: `ProductList` kartında kapak (`next/image`, `unoptimized`, `alt` ürün adından, ölçüler sözleşmeden) ve detay bağlantısı. `(shop)/products/[slug]`: galeri (büyük görsel ve küçük resimler; istemci bileşeni yalnızca seçim için), bilgiler, `not-found`, `loading`, `error`, `generateMetadata`. Metinler.
- [x] 2.11 **Test (web):** fotoğraf yöneticisi (yükleme hatası eşlemesi, sıralama isteği, silme), yayın eylemleri, kartta kapak ve fotoğrafsız kart, detay sayfası (galeri seçimi, 404), media Route Handler başlık iletimi.
- [x] 2.12 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 3 · Admin kaldırma: `api` → sözleşme → `web`

Bu faz bittiğinde admin uygunsuz ilanı gerekçeyle kaldırır ve yeniden açar.

- [x] 3.1 `ProductModerationService`: liste (durum süzgeci; ürün, üretici ve kapak; N+1 yok), `remove`, `reinstate` (D1, D7). Geçmiş kaydı, satıcıya hesabının dilinde e-posta, gönderim hatası akışı kesmez.
- [x] 3.2 DTO'lar ve `AdminProductController` (`/api/v1/admin/products`); `SecurityConfig` (`hasRole("ADMIN")`); e-posta metinleri (tr, en).
- [x] 3.3 `openapi.yaml` yeniden üretilir.
- [x] 3.4 **Test (`critical`):**
  - kaldırma vitrinden ve detaydan düşürür;
  - satıcı kaldırılan ilanı yayına alamaz (409) ama düzenleyebilir;
  - yeniden açma `PUBLISHED` yapar ve gerekçeyi temizler;
  - gerekçesiz kaldırma 400;
  - admin olmayan 403, oturum yok 401;
  - İngilizce hesaba İngilizce e-posta;
  - e-posta hatasında karar geçerli;
  - liste sorgu sayısı;
  - geçmiş.
- [x] 3.5 **Doğrulama:** `./mvnw -B verify` temiz.
- [x] 3.6 `ReasonDialog`'u `components/`'e çıkar; `SellerDecisionActions` onu kullanır (davranış aynı, mevcut testler yeşil). `(admin)/admin/products` (sekmeler, satırda vitrin bağlantısı, kaldır ve yeniden aç); admin sayfaları arası sekme gezintisi. BFF `/api/admin/products/[id]/[action]`. Metinler.
- [x] 3.7 **Test (web):** kaldırma diyaloğu, yeniden açma, rolsüz kullanıcıya "bulunamadı", Route Handler beyaz listesi.
- [x] 3.8 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 4 · Uçtan uca doğrulama ve doküman

- [x] 4.1 `docker compose up --build` ile uçtan uca senaryo:
  1. `uretici@ornek.test` ilan oluşturur;
  2. iki fotoğraf yükler (biri EXIF yönlü telefon fotoğrafı benzeri);
  3. sırayı değiştirir;
  4. yayına alır; vitrinde kapak görünür; detay sayfasında galeri çalışır;
  5. `admin@ornek.test` ilanı kaldırır; mailpit'te e-posta gelir; vitrinden düşer;
  6. satıcı düzeltir; admin yeniden açar;
  7. satıcı arşivler.

  Konteyner yeniden kurulduğunda fotoğraflar volume'da durur.
- [x] 4.2 Ekranların 375 ve 1440 px'de, açık ve koyu temada başsız tarayıcı ekran görüntüsü: satıcı listesi, ilan düzenleme ve fotoğraflar, vitrin kartı, detay sayfası, admin ilan listesi, kaldırma diyaloğu.
- [x] 4.3 Design D1–D7 → ADR-047…053, `docs/adr/index.md`, mkdocs nav.
- [x] 4.4 `01-domain-model.md` (ER), `02-api-reference.md`, `03-design-system.md` (yeni bileşenler: `ReasonDialog`, fotoğraf yöneticisi, galeri).
- [x] 4.5 Feature dosyası `docs/features/urun-ilanlari.md`; `features/index.md` (planlanan listeden çıkar); `vitrin-ve-arama.md` ve `satici-kaydi-ve-onayi.md` Tarihçe; `yerel-gelistirme.md` (dev satıcı hesabı).
- [x] 4.6 `00-current-state.md`: L25 kalkar (hesaplı seed satıcısı); test sayıları; sıradaki adım.
- [x] 4.7 Delta spec'ler ana spec'lere sync edilir: yeni `specs/product-listings/spec.md` (Purpose bölümüyle); `storefront-catalog` MODIFIED ve ADDED.
- [x] 4.8 `./docs/.tools/serve.sh --build` strict derleme temiz.
- [x] 4.9 Archive kullanıcı onayıyla yapıldı (2026-10-08). **İnsan işi, açık:** gerçek cihazda (telefon kamerasından yükleme) doğrulama; archive'ı bloke etmedi, notlandı.

# Ürün İlanları

**Durum:** Yayında
**İlk sürüm:** 2026-10-08
**Son değişiklik:** 2026-10-08

| | |
|---|---|
| **Ürün belgesi** | [Ürün İlanları](../product/urun-ilanlari.md) |
| **Kararlar** | Çerçeve: [ADR-038](../adr/038-listings-publish-immediately-with-admin-takedown.md), [ADR-039](../adr/039-uploads-on-local-volume-served-by-api.md), [ADR-044](../adr/044-seller-role-follows-status-with-query-gate.md). Uygulama: [ADR-047](../adr/047-listing-states-removed-and-named-transitions.md) … [ADR-053](../adr/053-admin-listing-moderation-shared-reason-dialog.md) |
| **Spec (capability)** | `openspec/specs/product-listings` (yeni); `openspec/specs/storefront-catalog` (kapak fotoğrafı, ürün detayı) |
| **Change'ler** | `openspec/changes/archive/2026-10-08-add-product-listings` (archive edildi) |
| **Rehberler** | [Yerel geliştirme](../how-to/yerel-gelistirme.md), [Admin yetkisi verme](../how-to/admin-yetkisi-verme.md) |
| **Referans** | [Domain modeli](../reference/01-domain-model.md), [API referansı](../reference/02-api-reference.md), [Tasarım sistemi](../reference/03-design-system.md) |
| **Kod** | `api/src/main/java/com/bahceden/catalog/` (`ListingService`, `PhotoProcessor`, `ExifOrientation`, `ProductModerationService`), `web/src/app/(seller)/seller/products/`, `web/src/app/(shop)/products/`, `web/src/app/(admin)/admin/products/`, `web/src/app/media/`, `web/src/features/listings/` |

---

## Ne yapar

Onaylı üretici "Ürünlerim" ekranından yeni ilan açar: ad, birim, fiyat ve stok girer, ilan taslak olarak kaydedilir. Ardından telefonundan 1–5 fotoğraf yükler. Fotoğraflar sunucuda doğru yöne çevrilir, küçültülür ve konum bilgisinden arındırılır. İlk fotoğraf kapaktır; sıra öne / arkaya düğmeleriyle değişir. Fotoğrafı olan taslak tek dokunuşla yayına girer ve vitrinde kapak fotoğrafıyla görünür. Alıcı ürünün sayfasında fotoğrafları, açıklamayı ve üreticiyi görür. Satıcı ilanı yayından çekebilir, arşivleyebilir ve geri alabilir. Yönetici uygunsuz bir ilanı gerekçeyle kaldırabilir; satıcı gerekçeyi görür ve e-postayla alır, ilanı düzeltir, yayına yönetici yeniden alır.

## Bilinçli sınırlar

- **Sepet, sipariş ve stok düşme yok** (KALANLAR A1). Stok satıcının elle güncellediği bir sayıdır.
- **Kategori, arama, filtre ve varyant yok.**
- **Kargo bilgisi yok** (KALANLAR A2).
- **Alıcı ilan şikâyet edemez.**
- **Seed ürünleri fotoğrafsızdır.** Liquibase volume'a dosya yazamaz; kart ve detay sayfası fotoğrafsız ürünü yer tutucu olmadan gösterir.
- **HEIC / WebP kabul edilmez**; ImageIO'nun okuyamadığı JPEG çeşitleri (örn. CMYK) reddedilir.
- **Görüntü işleme istek içinde yapılır**; arka plan kuyruğu yok (§3).

## Açık konular

- Gerçek cihazda, özellikle telefon kamerasından yükleme akışında insan doğrulaması. Başsız Chrome ile 375 / 1440 px ve iki temada ekran görüntüsü alındı.
- Fotoğraf işleme süresi ölçülecek; gerekirse sınırlar ayarlanır.

## Tarihçe

- 2026-10-08: `add-product-listings` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-08-add-product-listings/`). Ön koşullar: PR #2 `main`'e merge edildi ve CI 4/4 yeşil (run `37792192822`); delta spec'ler ana spec'lere sync edildi; D1–D7 → ADR-047…053; dokümanlar güncel. Taşımada göreli linkler bir seviye uzatıldı. Gerçek cihaz doğrulaması açık ve notlu.
- 2026-10-08: İlk sürüm (change `add-product-listings`).
  - `api`:
    - satıcı ilanları (`/api/v1/sellers/me/products`): taslak, düzenleme, fotoğraf yükleme / silme / sıralama, yayına alma / çekme / arşiv / geri alma;
    - `PhotoProcessor` (başlıktan piksel sınırı, EXIF yönü, 400 ve 1600 px JPEG, meta veri atma);
    - herkese açık fotoğraf ucu (yalnızca `product_photos`'ta kayıtlı anahtar);
    - vitrin kapağı ve `GET /api/v1/products/{slug}`;
    - admin ilan denetimi (`/api/v1/admin/products`; kaldır, yeniden aç; e-posta).
  - Şema: `009-product-listings.sql`, `010-dev-seed-product-listings.sql`.
  - `web`:
    - `/seller/products`, `/seller/products/new`, `/seller/products/[id]` (bilgiler, fotoğraflar, yayın);
    - vitrin kartında kapak ve `/products/[slug]` galerisi;
    - `/admin/products`;
    - `/media/product-photos/[key]` aktarma ucu;
    - başlıkta "Ürünlerim".
  - Ortaklaştırılan bileşenler: `Pagination`, `ListSkeleton`, `ReasonDialog`, `Slugs`.
  - Testler: 162 `api` (+29), 148 `web` (+55).
  - Doğrulama: `docker compose` üzerinde EXIF yönlü fotoğrafla uçtan uca senaryo (19 adım) ve volume kalıcılığı.
  - Kararlar: ADR-047…053.

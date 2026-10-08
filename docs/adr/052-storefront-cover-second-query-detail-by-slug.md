# [ADR-052] Vitrin kapağı ikinci sabit sorguyla gelir; ürün detayı `slug` ile, vitrinle aynı görünürlük koşuluyla

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-product-listings` design.md D6

---

## Context

Vitrin listesi sayfalıdır ve sorgu sayısı testle sabittir ([ADR-011](011-fetch-join-with-query-count-test.md)). Dev seed'inden gelen eski ürünlerin fotoğrafı yoktur. Alıcının ürünü ayrıntılı görebileceği bir sayfa yoktu.

## Decision

- `ProductSummary`'ye `coverPhoto` (boş olabilir) eklenir. Liste sorgusu değişmez. Sayfanın ürün kimlikleriyle **tek** bir ek sorgu kapakları getirir; sorgu sayısı en çok 3'tür.
- `GET /api/v1/products/{slug}` (`ProductDetail`): ürün, üretici ve sıralı fotoğraflar. Koşul vitrinle aynıdır (`PUBLISHED` ve satıcı `APPROVED`); aksi halde 404 `PRODUCT_NOT_FOUND`.
- `web`'de `/products/[slug]` sayfası: galeri, fiyat, stok, üretici, açıklama, `not-found` ve `loading`. Vitrin kartı detaya bağlanır.
- Fotoğrafsız ürün tipografik düzenle gösterilir; yer tutucu görsel yoktur.

| Alternatif | Neden reddedildi |
|---|---|
| Fetch join ile fotoğrafları liste sorgusunda getirmek | Sayfalama bellekte yapılır. |
| Kapağı ürün satırına kopyalamak | İki kaynaklı gerçek oluşur. |
| Kapağı zorunlu yapmak | Seed ürünleri sözleşmeyi ihlal eder. |

## Consequences

### Olumlu
- Listede görünmeyen ürün doğrudan adresle de açılmaz.
- Sorgu sayısı ürün sayısından bağımsız kalır.

### Olumsuz
- Liste bir sorgu daha yapar.

### Nötr / Trade-off
- Ürün detayı vitrin listesi gibi 30 saniye önbelleklenir; kaldırılan ilan en fazla bu kadar daha görünebilir.

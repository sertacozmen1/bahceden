# Vitrin ve Arama

**Durum:** Yayında
**İlk sürüm:** 2026-10-06
**Son değişiklik:** 2026-10-06

| | |
|---|---|
| **Ürün belgesi** | [product/index.md](../product/index.md) |
| **Kararlar** | [ADR-010](../adr/010-product-status-as-enum.md), [ADR-011](../adr/011-fetch-join-with-query-count-test.md), [ADR-012](../adr/012-page-size-capped-on-server.md), [ADR-013](../adr/013-minimal-seller-table-ahead-of-onboarding.md), [ADR-014](../adr/014-dev-seed-via-profiled-repeatable-migration.md), [ADR-015](../adr/015-contract-freshness-enforced-by-test.md) |
| **Spec (capability)** | `openspec/specs/storefront-catalog` |
| **Change'ler** | `openspec/changes/archive/2026-10-06-add-storefront-product-listing` (archive edildi) |
| **Rehberler** | [Yeni API ucu ekleme](../how-to/yeni-api-ucu-ekleme.md) |
| **Kod** | `api/src/main/java/com/bahceden/catalog/`, `web/src/app/(shop)/`, `web/src/features/catalog/` |

---

## Ne yapar

Alıcı, giriş yapmadan vitrine girer ve üreticilerin yayınladığı ürünleri görür. Her ürün adıyla, birim fiyatıyla, satış birimiyle (kg / adet / demet) ve **kimden geldiğiyle** listelenir: üreticinin adı ve ili ürünün yanında durur, çünkü ürünün ayırt edici değeri budur. Liste yayın tarihine göre yeniden eskiye sıralıdır ve sayfalanır. Henüz yayınlanmamış veya sezonu kapanmış ürünler vitrinde görünmez.

## Bilinçli sınırlar

- **Arama, filtre, sıralama ve kategori yok.** Liste sabit sırada; bunlar sonraki dilimde gelecek.
- **Ürün detay sayfası yok.** Liste tek ekrandır; ürün açıklaması şemada var ama sözleşmede yok.
- **Ürün görseli yok** (`openspec/KALANLAR.md` B3 kararı bekliyor). Tasarım yer tutucu gri kutular koymak yerine tipografiye yaslanıyor.
- **Sepet ve satın alma yok** (KALANLAR A1, A2).
- **Üretici kaydı yok** ([ADR-013](../adr/013-minimal-seller-table-ahead-of-onboarding.md)): üreticiler bugün yalnızca geliştirme seed'i ile oluşur; satıcı başvuru ve onay akışı KALANLAR A3'e bağlı.
- **Tek dil (Türkçe)** (KALANLAR A4); metinler tek modülde toplandı, kapı açık.

## Açık konular

- `sellers` tablosu A3 kararıyla genişleyecek; bu kabul edilmiş bir maliyet ([ADR-013](../adr/013-minimal-seller-table-ahead-of-onboarding.md)) ve [güncel durumda](../reference/00-current-state.md) izleniyor.
- Vitrin görünümünün 375 px ve 1440 px'de, açık ve koyu temada **gözle doğrulaması yapılmadı**; CSS mobil önce ve iki temayı da tanımlıyor ama insan doğrulaması bekliyor.
- Ürün birimi listesinin (`KILOGRAM`, `PIECE`, `BUNCH`) yeterliliği: yeni birim ihtiyacı doğarsa migration ve enum birlikte genişleyecek.
- Uzun vadede sıralamanın "yeniden eskiye" mi kalacağı yoksa alaka/öne çıkarma mı olacağı arama dilimine bırakıldı.

## Tarihçe

- 2026-10-06: `add-storefront-product-listing` change'i archive edildi; delta spec ana spec'e (`openspec/specs/storefront-catalog/spec.md`) sync edildi. Açık kalan tek iş vitrinin iki ekran genişliği ve iki temada gözle doğrulanması.
- 2026-10-06: İlk sürüm. Yürüyen iskelet olarak vitrin ürün listesi uçtan uca kuruldu: `docker-compose` (db, api, web, mailpit), Spring Boot 3.5.16 `api` (Liquibase `001` katalog changeset'i, `catalog` domaini, `GET /api/v1/products`, ortak sayfalama ve hata gövdesi, üretilen OpenAPI sözleşmesi + tazelik testi), Next.js 16 `web` (vitrin listesi, dört durum, tasarım token'ları, tek HTTP katmanı). 18 `api` testi ve 6 `web` testi yeşil; D1–D6 kararları ADR-010…015 olarak yazıldı (`add-storefront-product-listing`).

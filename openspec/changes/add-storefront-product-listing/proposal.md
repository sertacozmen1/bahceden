# add-storefront-product-listing

> **Durum:** Proposed — kullanıcı onayı bekliyor (`AGENTS.md` §5.3).
> **Not:** `openspec` CLI bu makinede kurulu değil; artefaktlar `AGENTS.md` §5.2'deki yapıya göre elle yazıldı.

## Why

Depoda kural ve dokümantasyon apparatus'u var ama **tek satır uygulama kodu yok**: `pom.xml`, `package.json`, `docker-compose.yml`, Flyway, sözleşme üretimi, tip üretimi ve test hatlarının gerçekten çalıştığı hiç doğrulanmadı. CI 4/4 yeşil ama `api` ve `web` job'ları kod olmadığı için atlanıyor (`docs/reference/00-current-state.md` L4).

Bu change bir **yürüyen iskelet** kurar: en küçük anlamlı ürün davranışını (vitrinde ürün listesi) uçtan uca çalıştırarak tüm araç zincirini kanıtlar. Kimlik, ödeme, kargo ve dosya yükleme gibi karar bekleyen alanlara dokunmaz; o dilimler bu iskeletin üstüne oturur.

Seçilen davranış rastgele değil: ürünün değer önerisinin görünür olduğu yer vitrindir ("tazeyi, **kimden geldiğini bilerek** almak", `docs/product/index.md`). Bu yüzden liste, ürünü üreticisiyle birlikte gösterir.

## What Changes

**Yeni (uygulama kodu ilk kez):**

- `docker-compose.yml`: `db` (PostgreSQL 17), `api`, `web`, `mailpit`.
- `api/`: Spring Boot 3 iskeleti (Maven wrapper, Dockerfile, profiller), Flyway `V1`, `catalog` domaini (ürün + üretici), `GET /api/v1/products` (sayfalı, filtresiz), ortak hata gövdesi ve sayfalama şekli, sözleşme üretimi + tazelik testi.
- `shared/contracts/openapi.yaml`: ilk üretilmiş sözleşme (commit'lenir, [ADR-009](../../../docs/adr/009-contract-generated-from-spring.md)).
- `web/`: Next.js iskeleti (App Router, TypeScript strict), `shared/generated` tip üretimi, `(shop)` rota grubu, vitrin liste sayfası ve dört durumu, tasarım token'ları, tek `apiClient`, `money` yardımcısı.
- Testler: `api` birim + Testcontainers + `MockMvc` sözleşme testi + sözleşme tazelik testi; `web` Vitest bileşen testi.
- Dokümanlar: `docs/features/vitrin-ve-arama.md` (yeni feature dosyası), `docs/how-to/yeni-api-ucu-ekleme.md`, `docs/reference/01-domain-model.md`, `docs/reference/02-api-reference.md`, güncellenen `00-current-state.md`, `mkdocs.yml` nav.

**Değişen:** `.github/workflows/ci.yml` artık gerçekten `api` ve `web` adımlarını koşar (guard'lar devreye girer, dosya değişmez).

## Capabilities

| Capability | Durum | Kapsam |
|---|---|---|
| `storefront-catalog` | ADDED | Vitrinde yayınlanmış ürünlerin üreticisiyle birlikte sayfalı listelenmesi |

## Impact

| Katman | Etki |
|---|---|
| `api/` | **Sıfırdan kurulur.** `catalog` domaini: `Product`, `Seller` entity'leri, repository, service, controller, DTO + `@Schema`. Ortak altyapı: `@RestControllerAdvice` hata gövdesi, sayfalı yanıt şekli, `Clock` bean'i, para tipi |
| `web/` | **Sıfırdan kurulur.** `(shop)` rota grubu, liste sayfası, `lib/{apiClient,money,env}.ts`, CSS token dosyası, dört durum dosyası |
| `shared/` | `contracts/openapi.yaml` ilk kez üretilir ve commit'lenir; `generated/` gitignore'da |
| DB | `V1__create_catalog.sql`: `sellers`, `products` tabloları + indeksler. Geliştirme verisi dev profiline bağlı idempotent seed ile (K6) |
| `docs/` | Yeni feature dosyası + how-to + iki referans sayfası + güncel durum + nav |
| CI | `api` ve `web` job'ları ilk kez gerçek iş yapar |

## Non-goals

Bu change **kapsam dışı** bırakır; her biri ayrı bir dilimdir:

- **Kimlik doğrulama ve oturum.** Uç nokta herkese açıktır; `ROLE_*` kontrolü ve giriş akışı bu dilimde yok ([ADR-008](../../../docs/adr/008-jwt-auth-with-opaque-refresh.md) uygulanmıyor, yalnızca ileride oturacak yer bırakılıyor).
- **Satıcı kaydı, doğrulaması ve onayı** (KALANLAR A3). `sellers` tablosu bu dilimde yalnızca görüntülenen bir ad ve şehirdir; hesapla ilişkisi, başvuru ve onay durumu yok.
- **Ürün ekleme/düzenleme ekranı ve fotoğraf yükleme** (KALANLAR B3). Liste yalnızca okuma; görsel alanı sözleşmede yer almaz.
- **Sepet, sipariş, ödeme, kargo** (KALANLAR A1, A2).
- **Arama, filtre, sıralama ve kategori ağacı.** Liste sabit sırada (yayın tarihi, yeniden eskiye) ve sayfalıdır; filtre parametreleri bir sonraki dilimde gelir.
- **Stok düşümü ve rezervasyon.** Stok alanı gösterilir ama değiştiren akış yok.
- **Çoklu dil** (KALANLAR A4). Metinler tek modülde toplanır ama tek dil (`tr`).

## Riskler

| Risk | Karşılık |
|---|---|
| Araç zinciri ilk kez kurulduğu için beklenmeyen sürtünme (Maven wrapper, Testcontainers, tip üretimi) | Dilim bilinçli olarak küçük: tek okuma ucu, tek ekran. Her faz tek başına çalışır durumda bırakılır |
| `sellers` tablosunun A3 kararından önce doğması, sonradan şema göçü gerektirebilir | Tablo bilinçli olarak minimum tutulur (ad, şehir, slug); hesap ilişkisi ve onay alanları A3 ile `V2` migration'ı olarak gelir. Bu kabul edilmiş bir maliyettir ve `design.md` D4'te gerekçelidir |
| Üretilen sözleşmenin zayıf çıkması (açıklamasız alanlar) | `@Schema` disiplini `tasks.md`'de ayrı bir görev; sözleşme testi alan adlarını ve zorunlulukları sabitler |

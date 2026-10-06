# Tasks: add-storefront-product-listing

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir; dış/insan işi açık kalır ve notlanır (K6).
> Faz sonlarında `AGENTS.md` §7 doğrulama komutları koşulur ve çıktılar raporlanır.

## Faz 1 · Ortam ve `api` iskeleti ayağa kalkar

- [ ] 1.1 `docker-compose.yml`: `db` (postgres:17, named volume, healthcheck), `api` (8080), `web` (3000), `mailpit` (8025). Ortam değişkenleri `.env.example` dosyalarında belgelenir; gerçek değer commit'lenmez (K10).
- [ ] 1.2 `api/pom.xml`: Java 21, Spring Boot 3.5.x BOM, [ADR-002](../../../docs/adr/002-dependency-budget.md) bütçesindeki bağımlılıklar (web, data-jpa, validation, security, oauth2-resource-server, postgresql, flyway-core, springdoc-webmvc-api) ve test bağımlılıkları. Sürümler pin'li.
- [ ] 1.3 Maven wrapper üretimi (`mvn wrapper:wrapper`, IntelliJ bundle'ı ile bir kez) → `api/mvnw` depoya girer.
- [ ] 1.4 `api/Dockerfile` (çok aşamalı: build + jre), `.dockerignore`.
- [ ] 1.5 `BahcedenApplication` + `application.yaml` (`ddl-auto: validate`, Flyway açık) + `application-dev.yaml`; `Clock` bean'i (`AGENTS.md` §11.3).
- [ ] 1.6 `SecurityConfig`: varsayılan **deny**, `/actuator/health` ve `/v3/api-docs/**` açıkça serbest; CORS yalnızca `web` origin'ine (ortamdan).
- [ ] 1.7 `docker compose up` ile `api` ayağa kalkar ve sağlık ucu 200 döner. **Doğrulama:** komut çıktısı rapora.

## Faz 2 · Veri modeli ve repository

- [ ] 2.1 `V1__create_catalog.sql`: `sellers` (D4 kapsamı), `products` (`status` enum'u metin + `CHECK`, `price_amount BIGINT`, `currency`, `unit`, `stock`, `published_at`), indeksler (`slug` tekil, `status = 'PUBLISHED'` kısmi).
- [ ] 2.2 `catalog/domain/`: `Seller`, `Product` entity'leri, `ProductStatus` ve `ProductUnit` enum'ları, `Money` değer nesnesi (minor unit, [ADR-006](../../../docs/adr/006-money-as-minor-unit.md)).
- [ ] 2.3 `ProductRepository`: `findPublished(Pageable)` — `join fetch seller` (D2).
- [ ] 2.4 `R__dev_seed_catalog.sql`: dev profiline bağlı, idempotent, sahte veri (D5, K10).
- [ ] 2.5 **Test:** Testcontainers PostgreSQL ile repository testi — yalnızca `PUBLISHED` ürünler döner, sıralama doğru, **sorgu sayısı sabit** (D2). `@Tag("integration")`.
- [ ] 2.6 **Test (`critical`):** `Money` yuvarlama ve kuruş dönüşümü ([ADR-006](../../../docs/adr/006-money-as-minor-unit.md)).

## Faz 3 · Uç nokta ve sözleşme

- [ ] 3.1 `catalog/dto/`: `ProductSummary`, `SellerSummary`, `MoneyView`, `PageResponse<T>` `record`'ları; her alanda Türkçe `@Schema(description, example)` (K2).
- [ ] 3.2 `ProductMapper` (elle yazılır), `ProductService` (sayfa boyutu sınırlaması — D3), `ProductController` (`GET /api/v1/products`, `permitAll` açıkça işaretli).
- [ ] 3.3 `common/`: `@RestControllerAdvice` ile tek hata gövdesi (`code`, `message`, `fieldErrors`), `PageResponse` şekli, `ApiErrorCode` enum'u (`AGENTS.md` §11.5).
- [ ] 3.4 Sözleşme üretimi: `/v3/api-docs.yaml` çıktısını `shared/contracts/openapi.yaml` dosyasına yazan test; `--check` modunda fark varsa kırmızı (D6). Üretilen dosya commit'lenir.
- [ ] 3.5 **Test:** `MockMvc` sözleşme testi — alan adları, zorunluluklar, sayfalama şekli, para biçimi; `size` üst sınırının sunucuda çekildiği (D3); boş liste yanıtı. `@Tag("critical")`.
- [ ] 3.6 **Doğrulama:** `./mvnw -B verify` temiz; `shared/contracts/openapi.yaml` üretildi ve diff'te görünüyor.

## Faz 4 · `web` vitrin ekranı

- [ ] 4.1 `web/package.json` (bütçe: next, react, react-dom + dev araçları), `tsconfig.json` (`strict: true`), `next.config.ts`, `.env.example`.
- [ ] 4.2 `npm run gen:api`: `shared/contracts/openapi.yaml` → `shared/generated/api.ts` (gitignore'da). Script `package.json`'da tanımlı ve CI'da koşuyor.
- [ ] 4.3 `web/src/lib/`: `env.ts` (doğrulanmış ortam okuma), `apiClient.ts` (tek HTTP katmanı, §11.5 hata gövdesi çözümü), `money.ts` (`Intl` ile minor unit → gösterim), `texts.ts` (tek metin modülü).
- [ ] 4.4 `web/src/app/(shop)/page.tsx`: Server Component, sunucu tarafında veri çeker; ürün kartı listesi (ad, üretici adı + şehir, birim fiyat, stok durumu).
- [ ] 4.5 **Dört durum** (§12.5): `loading.tsx` (iskelet), boş durum (yönlendirici metin), `error.tsx` (ne oldu + yeniden dene), `not-found.tsx`. Boş beyaz sayfa yok.
- [ ] 4.6 Stil: `app/globals.css` token dosyası (renk, aralık, tipografi; açık/koyu), `page.module.css`; mobil önce, tek kırılım (`min-width: 1024px`). Inline `style` yok.
- [ ] 4.7 Sayfalama kontrolü (önceki/sonraki), erişilebilir: etiketli bağlantılar, klavyeyle gezinilebilir, odak görünür.
- [ ] 4.8 **Test:** Vitest + Testing Library — liste render'ı (rol/erişilebilir ad ile), boş durum, hata durumu; ağ mock'lu (`AGENTS.md` §12.6).
- [ ] 4.9 **Doğrulama:** `npm --prefix web run lint`, `typecheck`, `test`, `build` temiz; `docker compose up` ile 375 px ve 1440 px'de, açık ve koyu temada gözle doğrulama (ekran görüntüleri depo dışında).

## Faz 5 · Doküman ve kapanış

- [ ] 5.1 `docs/features/vitrin-ve-arama.md`: yeni feature dosyası (`template.md` kopyası) + `features/index.md` tablosuna satır + nav.
- [ ] 5.2 `docs/how-to/yeni-api-ucu-ekleme.md`: DTO → `@Schema` → sözleşme üretimi → tip üretimi → ekran → test akışı, gerçek komutlarla; `how-to/index.md` tablosu ve nav.
- [ ] 5.3 `docs/reference/01-domain-model.md`: ER diyagramı (mermaid) + tablo/kolon dökümü.
- [ ] 5.4 `docs/reference/02-api-reference.md`: uç nokta, sayfalama, hata gövdesi ve para biçimi sözleşmesi.
- [ ] 5.5 `docs/reference/00-current-state.md`: katman durumları, L1/L4 güncellemesi, D4'ün bıraktığı bilinen sınırlama (`sellers` tablosu A3 ile genişleyecek).
- [ ] 5.6 `docs/features/_genel.md` *Tarihçe*: tek satır (K1).
- [ ] 5.7 `./docs/.tools/serve.sh --build` strict temiz; `mkdocs.yml` nav tam.
- [ ] 5.8 D1–D6 kararları için ADR'ler yazılır (`docs/adr/010…`) ve `adr/index.md` güncellenir — **archive ön koşulu** (`AGENTS.md` §5.3).
- [ ] 5.9 Delta spec `openspec/specs/storefront-catalog/spec.md` ana spec'e sync edilir.
- [ ] 5.10 CI 4/4 yeşil (bu kez `api` ve `web` job'ları gerçekten koşarak). **Doğrulama:** run numarası rapora.
- [ ] 5.11 Rapor `AGENTS.md` §14 formatında; insan review'una sunulur. Archive **kullanıcı onayı** ister (§9).

## Bu changeın kapsamadığı, sıradaki dilim adayları

- Kimlik ve oturum ([ADR-008](../../../docs/adr/008-jwt-auth-with-opaque-refresh.md) uygulaması).
- Ürün detay sayfası ve slug ile tek ürün ucu.
- Arama, filtre, kategori.

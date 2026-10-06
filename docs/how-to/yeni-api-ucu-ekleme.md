# Nasıl Yapılır: Yeni bir API ucu ekleme (sözleşmeden ekrana)

**Özet:** Bu rehber, yeni bir veri alanını veya uç noktayı `api`'den `web` ekranına kadar hangi sırayla ekleyeceğinizi gösterir. Sıra keyfi değil: sözleşme ile kodun ayrışmaması buna bağlı ([ADR-009](../adr/009-contract-generated-from-spring.md), `AGENTS.md` K2).

## Ön koşullar

- [Yerel geliştirme ortamı](yerel-gelistirme.md) kurulu: JDK 21, Node 20+, Docker.
- `docker compose up` ile ortam ayakta (`db`, `api`, `web`).
- Değişiklik bir spec'i etkiliyorsa önce OpenSpec change (`AGENTS.md` §5.1); küçük bir ekleme ise doğrudan devam edin ama [feature kaydına](../features/index.md) satır bırakmayı unutmayın.

---

## Akışın özeti

```text
DTO + @Schema  →  openapi.yaml (üretilir)  →  web tipleri (üretilir)  →  ekran  →  test
```

Ters yönde çalışmak (önce ekranı yazıp sonra alanı uydurmak) sözleşmede olmayan bir alanın UI'a girmesine yol açar ve bu yasaktır (`AGENTS.md` K2).

---

## Adımlar

### 1. Gerekiyorsa şemayı değiştirin

Yeni bir kolon gerekiyorsa **yeni** bir Liquibase changeset yazın; uygulanmış changeset düzenlenmez ([ADR-016](../adr/016-liquibase-for-schema-migrations.md)):

```bash
# 1) api/src/main/resources/db/changelog/<NNN>-<kebab-case>.sql  (formatted SQL)
# 2) db.changelog-master.yaml dosyasına include satırı ekleyin
```

Changeset başlığı ve geri alma satırları zorunludur:

```sql
--changeset bahceden:003-add-harvested-on
--comment: Ürüne hasat tarihi eklenir
alter table products add column harvested_on date;
--rollback alter table products drop column harvested_on;
```

> [!WARNING]
> Changelog dosyalarındaki **yorumlarda** `changeset` kelimesinin ardından
> `ad:değer` biçimi kullanmayın: formatted SQL ayrıştırıcısı bunu gerçek bir
> değişiklik bildirimi sanıp fantom bir kayıt üretir (bu hata bir kez yapıldı).

Entity'yi de aynı commit'te güncelleyin. `ddl-auto: validate` olduğu için uyumsuzluk uygulamayı **açılışta** düşürür; bu bir koruma, engel değil.

### 2. DTO'ya alanı ekleyin ve Türkçe `@Schema` yazın

Sözleşmenin kalitesi buradan gelir: açıklaması ve örneği olmayan alan, üretilen spec'i zayıflatır.

```java
@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Ürünün hasat tarihi", example = "2026-10-01")
LocalDate harvestedOn
```

> [!IMPORTANT]
> `requiredMode = REQUIRED` yazılmazsa springdoc alanı optional üretir ve `web` tarafında tipi `?` olur: her kullanımda gereksiz null kontrolü doğar. Alan gerçekten opsiyonel değilse bunu yazın.

### 3. Service ve controller katmanını güncelleyin

İş kuralı service'te, controller ince kalır (`AGENTS.md` §11.2). Yeni bir uç ekliyorsanız güvenlik kuralını **açıkça** yazın: varsayılan deny'dir, unutulan uç 403 döner ([ADR-007](../adr/007-query-level-ownership-scoping.md)).

```java
// SecurityConfig içinde
.requestMatchers(HttpMethod.GET, "/api/v1/products/*").permitAll()
```

### 4. Sözleşmeyi yeniden üretin

```bash
cd api
./mvnw test -Dtest=OpenApiContractTest -Dopenapi.write=true
```

Bu komut `shared/contracts/openapi.yaml` dosyasını günceller. Üretilen diff'i **commit'e dahil edin**: sözleşme değişikliğinin PR'da görünmesi bilinçli bir review aracıdır.

> [!WARNING]
> `openapi.yaml` dosyasını **elle düzenlemeyin** (`AGENTS.md` K8). Çıktı yanlışsa DTO veya anotasyonu düzeltip yeniden üretin.

### 5. `web` tiplerini üretin

```bash
npm --prefix web run gen:api
```

Çıktı `shared/generated/api.ts` dosyasına yazılır ve **commit'lenmez** (CI kendi üretir). Tipler `@contract` takma adıyla import edilir:

```ts
import type { components } from "@contract";

type ProductSummary = components["schemas"]["ProductSummary"];
```

Bu import yalnızca **tip** içindir (`import type`): derlemede silinir, çalışma zamanında dizin dışı bir bağımlılık doğmaz.

### 6. Veri katmanını ve ekranı yazın

- Veri çekme `web/src/features/<domain>/api.ts` içinde, tek HTTP katmanı (`lib/apiClient.ts`) üzerinden. İkinci bir `fetch` sarmalayıcı yazılmaz.
- Kullanıcıya görünen metin `lib/texts.ts` içine; bileşene hardcode metin yazılmaz.
- Para `lib/money.ts` ile biçimlendirilir; bileşende kuruş bölme yapılmaz.
- Ekranın **dört durumu** tasarlanır: yükleniyor (`loading.tsx`), boş, hata (`error.tsx`), yetkisiz (`AGENTS.md` §12.5).

### 7. Testleri yazın

| Katman | Zorunlu asgari |
|---|---|
| `api` sözleşme | `MockMvc` ile alan adları, zorunluluklar, hata gövdesi. Yeni iş kuralı ise `@Tag("critical")` |
| `api` veri | Yeni sorgu varsa Testcontainers testi; liste sorgusu ise **sorgu sayısı** iddiası ([ADR-011](../adr/011-fetch-join-with-query-count-test.md)) |
| `web` | Vitest ile render, boş durum, hata eşlemesi |

### 8. Tamamını doğrulayın

```bash
cd api && ./mvnw -B verify
npm --prefix web run lint
npm --prefix web run typecheck
npm --prefix web test
npm --prefix web run build
```

Sözleşme güncellenmemişse `OpenApiContractTest` kırmızı olur ve ne yapılacağını söyler.

### 9. Dokümanı güncelleyin

| Dosya | Ne zaman |
|---|---|
| [`reference/02-api-reference.md`](../reference/02-api-reference.md) | Uç nokta, alan veya hata kodu değişti |
| [`reference/01-domain-model.md`](../reference/01-domain-model.md) | Şema değişti |
| [`reference/00-current-state.md`](../reference/00-current-state.md) | Bir katmanın durumu veya bilinen sınırlama değişti |
| [`features/`](../features/index.md) *Tarihçe* | **Her iş** (`AGENTS.md` K1) |

---

## Olası hatalar

- **Hata:** `OpenApiContractTest` kırmızı: "Kod ile sözleşme ayrışmış"
  **Çözüm:** `./mvnw test -Dtest=OpenApiContractTest -Dopenapi.write=true` çalıştırıp üretilen diff'i commit'e ekleyin. Dosyayı elle düzeltmeyin.

- **Hata:** `web` tarafında alan `undefined` olabiliyor (`string | undefined`)
  **Çözüm:** DTO'da `requiredMode = Schema.RequiredMode.REQUIRED` eksik. Ekleyip 4. ve 5. adımı yeniden koşun.

- **Hata:** Yeni uç 403 dönüyor
  **Çözüm:** `SecurityConfig` varsayılan deny uygular; ucu açıkça yetkilendirin.

- **Hata:** Dev seed verisi test veya üretim ortamında görünüyor
  **Çözüm:** `spring.liquibase.contexts` o ortamda tanımlı mı kontrol edin. Liquibase, bağlam verilmezse bağlam etiketli changeset'leri **de** uygular; `contextFilter:dev` tek başına koruma sağlamaz ([ADR-016](../adr/016-liquibase-for-schema-migrations.md)).

- **Hata:** `Schema-validation: wrong column type encountered`
  **Çözüm:** Migration ile entity tipi uyuşmuyor. PostgreSQL `char(n)` tipini `bpchar` olarak raporlar; `varchar(n)` kullanın.

- **Hata:** Liste ucu eklendi, sorgu sayısı testi kırmızı
  **Çözüm:** İlişkiyi `join fetch` ile çekin ve sayım sorgusunu `countQuery` olarak açıkça yazın.

---

## İlgili kararlar

- [ADR-009](../adr/009-contract-generated-from-spring.md) — sözleşme Spring kodundan üretilir
- [ADR-015](../adr/015-contract-freshness-enforced-by-test.md) — tazelik testle zorunlu
- [ADR-016](../adr/016-liquibase-for-schema-migrations.md) — şema değişiklikleri Liquibase ile
- [ADR-012](../adr/012-page-size-capped-on-server.md) — sayfalama sınırı
- [ADR-006](../adr/006-money-as-minor-unit.md) — para temsili

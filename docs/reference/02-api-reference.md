# API Referansı

**Son doğrulama:** 2026-10-06 · Kaynak: `shared/contracts/openapi.yaml`

> Sözleşme `api/` içindeki DTO ve anotasyonlardan **üretilir** ([ADR-009](../adr/009-contract-generated-from-spring.md)) ve tazeliği bir testle zorunludur ([ADR-015](../adr/015-contract-freshness-enforced-by-test.md)). Bu sayfa sözleşmeyi okunur hale getirir; çelişki halinde **sözleşme dosyası** esastır.

---

## Ortak kurallar

### Sayfalı liste yanıtı

Tüm liste uçları aynı şekli kullanır (`AGENTS.md` §11.5):

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`size` alanı **uygulanan** sayfa boyutunu bildirir: istek üst sınırı aşarsa sunucu değeri sınıra çeker ([ADR-012](../adr/012-page-size-capped-on-server.md)).

### Hata gövdesi

Tüm hatalar aynı şekli kullanır:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Gönderilen değerler geçersiz.",
  "fieldErrors": [{ "field": "page", "message": "Sayfa numarası negatif olamaz" }]
}
```

- `code` İngilizce ve sabittir; istemci buna göre dallanır. Bugün üretilen kodlar: `VALIDATION_FAILED`, `INTERNAL_ERROR`.
- `message` Türkçedir ve kullanıcıya gösterilebilir.
- `fieldErrors` her zaman vardır; alan hatası yoksa boş dizidir.
- Stack trace, SQL ve iç sınıf adı gövdeye **sızmaz**.

### Para

```json
{ "amount": 12550, "currency": "TRY" }
```

Tutar **kuruş** cinsinden tamsayıdır ([ADR-006](../adr/006-money-as-minor-unit.md)). İstemci hesap yapmaz, biçimlendirir.

### Zaman

Tüm zaman damgaları UTC ve ISO-8601. Gösterim zaman dilimi istemcinin işidir.

---

## Uç noktalar

### `GET /api/v1/products`

Vitrinde yayınlanmış ürünleri listeler. **Kimlik doğrulaması gerektirmez** (`SecurityConfig` içinde açıkça `permitAll`).

**Sorgu parametreleri**

| Parametre | Tip | Varsayılan | Kural |
|---|---|---|---|
| `page` | `integer` | `0` | Sıfırdan başlar; negatif değer 400 döner |
| `size` | `integer` | `20` | En az 1; üst sınır sunucuda **50**'ye çekilir, aşan istek reddedilmez |

**Davranış**

- Yalnızca `PUBLISHED` ürünler döner; `DRAFT` ve `ARCHIVED` görünmez.
- Sıralama yayın tarihine göre yeniden eskiye; eşit tarihlerde ikincil ölçüt tanımlayıcıdır (sayfalar arası tutarlılık).
- Aralık dışı sayfa boş içerik ve doğru toplam döner; hata üretmez.

**200 yanıtı** — `PageResponse<ProductSummary>`

```json
{
  "items": [
    {
      "slug": "sultani-uzum",
      "name": "Sultani Üzüm",
      "unit": "KILOGRAM",
      "unitPrice": { "amount": 4250, "currency": "TRY" },
      "stockQuantity": 120,
      "seller": {
        "slug": "ornek-uretici-manisa",
        "displayName": "Örnek Üretici (Manisa)",
        "city": "Manisa"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

| Alan | Tip | Not |
|---|---|---|
| `slug` | `string` | Ürünün kalıcı tanımlayıcısı |
| `name` | `string` | Ürün adı |
| `unit` | `enum` | `KILOGRAM` / `PIECE` / `BUNCH` |
| `unitPrice` | `Money` | Birim fiyat |
| `stockQuantity` | `integer` | Sıfırsa stokta değil |
| `seller` | `SellerSummary` | Üretici: `slug`, `displayName`, `city` |

Sözleşmedeki tüm bu alanlar **zorunludur** (`required`): istemci tipleri optional alan görmez.

**400 yanıtı** — `ApiError`. Geçersiz `page`/`size` değerleri.

### `GET /api/v1/health`

Operasyonel sağlık kontrolü; konteyner healthcheck'i bunu kullanır. Spring Boot Actuator bilinçli olarak kullanılmıyor ([ADR-002](../adr/002-dependency-budget.md) bütçesi).

```json
{ "status": "UP" }
```

Derin kontrol (veritabanı erişimi) **yoktur**: sağlık ucu, bağımlılıkları yavaşken konteyneri yeniden başlatan bir mekanizmaya dönüşmemeli.

### `GET /v3/api-docs` · `GET /v3/api-docs.yaml`

Üretilmiş sözleşmenin kendisi. Swagger UI paketi alınmadığı için yalnızca spec uçları vardır.

---

## Henüz olmayan uçlar

Kimlik doğrulama (`/api/v1/auth/**`), satıcı ürün yönetimi, sepet, sipariş, ödeme ve gönderim uçları **yoktur**. Kimlik modeli karara bağlı ([ADR-008](../adr/008-jwt-auth-with-opaque-refresh.md)) ama uygulanmadı; diğerleri `openspec/KALANLAR.md` maddelerine bağlı.

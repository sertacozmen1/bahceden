# [ADR-048] Satıcı ilan uçları `/sellers/me/products` altında; önce onaylı satıcı kapısı, sonra sahiplik sorgusu

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-product-listings` design.md D2

---

## Context

İlanlar onaylı satıcıya aittir. Askıya alınan satıcının token'ında rol en fazla 15 dakika daha durabilir ([ADR-044](044-seller-role-follows-status-with-query-gate.md)). Satıcı sahipliği sorgu seviyesinde denetlenmelidir ([ADR-007](007-query-level-ownership-scoping.md)).

## Decision

- Uçlar `/api/v1/sellers/me/products` altındadır: liste, oluşturma, okuma, güncelleme, fotoğraflar ve `publish` / `unpublish` / `archive` / `restore`.
- `SecurityConfig` `hasRole("SELLER")` ister.
- Her servis metodunun ilk adımı `requireApprovedSeller(accountId)` çağrısıdır; ilan sonra `findByIdAndSellerId` ile okunur. Başka satıcının ilanı **404** döner. Rolü token'da duran ama onaylı olmayan satıcı **403** `SELLER_NOT_APPROVED` alır; `web` bu durumda hata yerine başvuru sayfasına yönlendiren bir panel gösterir.
- `slug` oluşturmada addan üretilir ve sonra değişmez. Üretim kuralı satıcıyla ortaktır (`common/text/Slugs`).

| Alternatif | Neden reddedildi |
|---|---|
| `/api/v1/products` altında `sellerId` parametresiyle yazma | İstemciden gelen kimliğe güvenmek; vitrin ucuyla karışır. |
| Yalnızca rol denetimi | Askıdaki satıcıya 15 dakikalık pencere açar. |
| Ad değişince `slug`'ı güncellemek | Paylaşılmış ürün bağlantıları kırılır. |

## Consequences

### Olumlu
- ADR-044'ün iki katmanı (rol + durum) ilk kez gerçek uçlarda uygulandı; ikisi de `critical` testle sabit.
- İstemci satıcı kimliği hiç göndermez.

### Olumsuz
- Her satıcı isteği satıcı kaydını bir kez sorgular.

### Nötr / Trade-off
- Ürün adı değiştiğinde adres eski adı taşımaya devam eder.

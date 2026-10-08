# [ADR-053] Admin ilan denetimi: liste ve iki eylem; gerekçe diyaloğu ortak bileşen

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-product-listings` design.md D7

---

## Context

[ADR-038](038-listings-publish-immediately-with-admin-takedown.md) §3 admin'in uygunsuz bir ilanı gerekçeyle kaldırabilmesini istiyor. Satıcı başvurularının admin yüzeyi `web` içinde zaten var ([ADR-046](046-admin-surface-in-web-first-admin-by-sql.md)).

## Decision

- `api` uçları `ROLE_ADMIN` ile korunur:
  - `GET /api/v1/admin/products?status=` (varsayılan `PUBLISHED`; üretici ve kapakla birlikte);
  - `POST …/{id}/remove {reason}`;
  - `POST …/{id}/reinstate`.

  Geçiş kuralları satıcı geçişleriyle aynı yerden uygulanır (`ListingService.apply`). Karar satıcıya hesabının dilinde e-postayla bildirilir; seed satıcılarında e-posta adımı atlanır.
- `web`'de `/admin/products`: "Yayında" ve "Kaldırılan" sekmeleri. Her satırda kapak, üretici, durum, vitrin bağlantısı ve tek eylem bulunur. Kaldırılmış ilanın gerekçesi satırda görünür. Admin sayfaları arasında sekme gezintisi (`AdminNav`) vardır.
- Gerekçe diyaloğu `components/ReasonDialog`'a çıkarıldı. Satıcı reddi, satıcı askısı ve ilan kaldırma aynı bileşeni ve aynı metinleri (`reasonDialog`) kullanır.

| Alternatif | Neden reddedildi |
|---|---|
| Admin için ayrı ilan detay sayfası | Vitrin sayfasının kopyası olur. |
| Kaldırmayı yalnızca satıcı askısıyla yapmak | Tek bir uygunsuz ilan için bütün satıcı cezalandırılır. |

## Consequences

### Olumlu
- Uygunsuz ilan, satıcıya dokunmadan tek başına kaldırılabilir.
- Gerekçe diyaloğu tek yerde; davranış ve metinler üç eylemde aynı.

### Olumsuz
- Admin kaldırılmış ilanın içeriğini vitrinde göremez; düzeltmeyi satıcıdan e-posta veya iletişim yoluyla takip eder.

### Nötr / Trade-off
- Alıcının ilan şikâyet etmesi bu kararın kapsamında değil.

# [ADR-047] İlan durumlarına `REMOVED` eklenir; geçişler adlandırılmış ve tek yerde

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-product-listings` design.md D1

---

## Context

`products.status` yalnızca `DRAFT`, `PUBLISHED` ve `ARCHIVED` değerlerini alıyordu. [ADR-038](038-listings-publish-immediately-with-admin-takedown.md) §3, admin'in bir ilanı gerekçeyle kaldırabilmesini ve satıcının kaldırılan ilanı kendisi yayına alamamasını istiyor. Satıcı ilanını yayından çekip yeniden yayına aldığında, vitrinde öne çıkmak için bu davranışın kötüye kullanılmaması da gerekiyor.

## Decision

- Durumlara `REMOVED` eklenir: admin tarafından kaldırılmış ilan. Ayrıca `status_reason` ve `status_changed_at` kolonları eklenir.
- İzinli geçişler `ProductTransition` enum'unda, eylemle adlandırılmış olarak tek yerdedir. Her geçiş kimin yapabileceğini (satıcı / admin) ve koşulunu taşır:

| Geçiş | Kim | Başlangıç → hedef | Koşul |
|---|---|---|---|
| `PUBLISH` | Satıcı | `DRAFT` → `PUBLISHED` | ≥ 1 fotoğraf |
| `UNPUBLISH` | Satıcı | `PUBLISHED` → `DRAFT` | - |
| `ARCHIVE` | Satıcı | `DRAFT`, `PUBLISHED` → `ARCHIVED` | - |
| `RESTORE` | Satıcı | `ARCHIVED` → `DRAFT` | - |
| `REMOVE` | Admin | `DRAFT`, `PUBLISHED` → `REMOVED` | gerekçe |
| `REINSTATE` | Admin | `REMOVED` → `PUBLISHED` | ≥ 1 fotoğraf |

- Her geçiş `product_status_changes` tablosuna yazılır. Tablo yalnızca eklenerek büyür ([ADR-040](040-seller-application-is-the-seller-record.md) ile aynı desen).
- Satıcı `REMOVED` ilanı düzenleyebilir, ama ondan geçiş yapamaz.
- `published_at` yalnızca ilk yayında yazılır, yeniden yayında değişmez.

| Alternatif | Neden reddedildi |
|---|---|
| `removed_by_admin` bayrağı | Durum ile bayrak geçersiz kombinasyonlar üretir (örneğin `ARCHIVED` + kaldırılmış). |
| Kaldırılan ilanı `ARCHIVED`'a çekmek | Satıcı arşivden geri alıp yayına alabilir; ADR-038 §3 delinir. |
| Yeniden yayında `published_at`'i güncellemek | Vitrinde öne çıkmak için aç-kapa yapmak ödüllendirilir. |

## Consequences

### Olumlu
- "Satıcı kaldırılan ilanı açamaz" kuralı tek bir koşula iner.
- Kimin hangi geçişi hangi koşulla yaptığı tek bir tabloda durur ve testle sabittir.

### Olumsuz
- Durum kısıtı yeni bir changeset'le genişledi. `REMOVED` satırı varken geri alma kısıtta düşer.

### Nötr / Trade-off
- İlk yayın tarihi sabit kaldığı için, uzun süre taslakta bekleyen bir ilan yeniden yayına alındığında listenin altında görünür.

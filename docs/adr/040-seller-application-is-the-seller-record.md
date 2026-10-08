# [ADR-040] Başvuru, satıcı kaydının kendisidir; durum geçmişi ayrı ve silinmeyen bir tabloda

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-seller-onboarding` design.md D1

---

## Context

[ADR-037](037-seller-application-with-document-and-manual-approval.md) satıcı başvurusunu ve dört durumu (`PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`) sabitledi. Reddedilen başvuru düzeltilip yeniden gönderilebilir. Her kararın kim tarafından, ne zaman ve hangi gerekçeyle verildiği saklanmalıdır.

`sellers` tablosu bugün vitrin için minimumdur ([ADR-013](013-minimal-seller-table-ahead-of-onboarding.md)) ve `Seller` entity'si `catalog` paketindedir. Oysa `AGENTS.md` §11.2 `seller`'ı ayrı bir domain olarak sayıyor.

## Decision

- **Ayrı başvuru tablosu yoktur.** Başvuru, `sellers` tablosunda `PENDING` durumunda açılan satırdır. Tabloya şu kolonlar eklenir:
  - `account_id`: tekil; NULL olabilir, çünkü seed satıcılarının hesabı yok;
  - `status`;
  - `phone`;
  - `status_reason`;
  - `status_changed_at`.
- **Her durum geçişi `seller_status_changes` tablosuna bir satır yazar.** Satırda önceki durum, yeni durum, gerekçe, geçişi yapan hesap ve zaman bulunur. Bu tablo yalnızca eklenerek büyür. Repository'si çıplak bir Spring Data `Repository`'dir: `delete` ve güncelleme yolu yoktur.
- **İzinli geçişler `SellerTransition` enum'unda tek yerdedir** ve eylemle adlandırılır: `RESUBMIT`, `APPROVE`, `REJECT`, `SUSPEND`, `REINSTATE`. Hedef duruma göre genel bir tablo yetmez: onay ile askıdan çıkarmanın ikisi de `APPROVED`'a gider, ama farklı durumlardan başlar.
- **`Seller` entity'si `com.bahceden.seller.domain`'e taşınır.** `catalog`, `Product → Seller` ilişkisi üzerinden ona bağlı kalır. Hesap bir kimlik (`accountId`) olarak tutulur, entity ilişkisi olarak değil; böylece iki domain birbirine kilitlenmez.

| Alternatif | Neden reddedildi |
|---|---|
| Ayrı `seller_applications` tablosu; onayda `sellers` satırının kopyalanması | Veri iki yerde durur. Yeniden gönderimde hangisinin geçerli olduğu belirsizleşir. |
| Geçmişi `sellers` satırındaki kolonlarda tutmak (`approved_by`, `rejected_at` …) | Yalnızca son geçiş kalır, denetim izi kaybolur. |
| `Seller`'ı `catalog` içinde bırakmak | Satıcının yaşam döngüsü katalogdan büyük; paket düzeni kuralı (§11.2) ayrı bir domain istiyor. |

## Consequences

### Olumlu
- Durum tek yerdedir: vitrin, admin listesi ve ileride satıcı paneli aynı satırı okur.
- Geçmiş değiştirilemez ve güncel durum sorgularını kirletmez.

### Olumsuz
- `sellers` tablosu iki adımda şekillendi (ADR-013'ün öngördüğü maliyet).
- Hesap ilişkisi kimlik olduğu için admin detayı hesap e-postalarını ayrı bir sorguyla (`findAllById`) okur.

### Nötr / Trade-off
- Seed satıcıları hesapsızdır. Ürünlerini yönetemezler; yalnızca vitrini beslerler.

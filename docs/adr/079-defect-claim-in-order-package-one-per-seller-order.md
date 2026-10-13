# [ADR-079] Ayıplı ürün talebi `order` paketinde; satıcı siparişi başına tek talep; satıcı siparişinin durumu değişmez, sonucu talep taşır

**Tarih:** 2026-10-13
**Durum:** Accepted
**Kaynak:** `add-defect-claims` design.md D1; ürün sahibinin 2026-10-13 kararı (tek talep, birden çok satır)

---

## Context

[ADR-056](056-cancel-before-shipment-and-defect-refund.md) §4–5 ayıplı ürün talebini karara bağladı: teslimden itibaren 48 saat, fotoğraf, satır bazlı talep, satıcı yanıtı, admin hakemliği ve kısmi iade.

Talep sipariş tutarlarına, komisyona ve iadeye dayanır. Teslim anı gönderide durur ([ADR-072](072-shipments-table-and-seller-order-shipping-states.md)). Satıcı siparişinin `REFUNDED` durumu ise teslim edilemeyen siparişin admin kararı için ayrılmıştır ([ADR-077](077-undelivered-order-admin-refund-or-close.md)).

## Decision

1. **Paket.** Talep `com.bahceden.order` paketinde `DefectClaim*` sınıflarıyla yaşar.
   - Teslim anını `shipping`'den okur; bu, mevcut `order` → `shipping` yönüyle aynıdır.
   - İadeyi `payment` üzerinden yapar.
   - Fotoğrafı `catalog.PhotoProcessor` ve `common.storage.FileStorage` ile işler (K9).
2. **Tek talep.** Satıcı siparişi başına tek talep vardır (`defect_claims.seller_order_id` benzersiz); talep birden çok satır taşıyabilir.
3. **Satıcı siparişinin durumu.** `DELIVERED` kalır. Talebin durumu ve sonucu (iade tutarı, yeni komisyon, yeni hakediş) talepte durur. Görünümler hakedişi talebin sonucundan okur.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Satıcı siparişine `CLAIMED` gibi yeni durumlar eklemek | Kısmi iade sonrası sipariş yine "teslim edildi"dir; iki durum makinesi iç içe geçer. |
| `REFUNDED` durumunu yeniden kullanmak | Teslim edilemeyen siparişin admin kararıyla karışır; admin listesi iki anlamı ayıramaz. |
| Satır başına ayrı talep | Her satıra ayrı karar ve ayrı iade gerekir; art arda taleplerle iade toplamı satır tutarını aşabilir. Ürün sahibi tek talebi seçti. |
| Ayrı bir `claim` paketi | Talebin bütün kuralları sipariş tutarlarına dayanır; ayrı paket `order`'ın iç tiplerini dışa açmayı gerektirir. |

## Consequences

### Olumlu
- İade toplamının satıcı siparişini aşması yapısal olarak önlenir.
- Satıcı siparişinin durum makinesi değişmez; mevcut süzgeçler ve testler etkilenmez.

### Olumsuz
- Hakediş artık iki yerden okunur: sipariş anındaki değer ve talebin sonucu. Görünümler ikisini birlikte göstermek zorundadır.
- Alıcı ilk talebinde bir satırı unutursa ikinci talep açamaz.

### Nötr / Trade-off
- Satıcı listesinde "talebi olan siparişler" süzgeci yoktur; satıcı talepleri sipariş detayında görür, e-posta ise talep açılınca gider.

# [ADR-072] Gönderi ayrı tabloda; satıcı siparişi kargo durumlarıyla genişler, ara gönderi durumları siparişe yansımaz

**Tarih:** 2026-10-10
**Durum:** Accepted
**Kaynak:** `add-shipments` design.md D2

---

## Context

Satıcı siparişi para, komisyon ve iptal birimidir ([ADR-064](064-three-level-order-snapshot-idempotency-key.md), [ADR-067](067-cancel-before-shipment-full-refund-restock.md)). Kargo gönderisi ise kendi yaşam döngüsü olan bir kayıttır: açılır, şubeye verilir, yola çıkar, teslim edilir ya da edilemez; kargoya verilmeden iptal edilip yeniden açılabilir. ADR-056 §1 "kargoya verilmeden önce" iptali serbest bırakır.

## Decision

- **`shipments` tablosu:** satıcı siparişi kimliği, entegratör adı ve anahtarı, kargo firması, takip numarası, koli boyutu, durum, etiketin depolama anahtarı ve içerik türü, kargoya verilme ve teslim zamanları, son sorgu zamanı. İptal edilen gönderi silinmez (§11.4). Bir satıcı siparişinin tek **etkin** gönderisi kısmi benzersiz indeksle zorunludur: `unique (seller_order_id) where status <> 'CANCELLED'`.
- **`ShipmentStatus`:** `CREATED` → `HANDED_OVER` → `IN_TRANSIT` → `DELIVERED` / `DELIVERY_FAILED`; `CANCELLED` yalnızca `CREATED`'dan. Sıralı ilerler; geri giden veya aynı bildirim yok sayılır.
- **`SellerOrderStatus` genişler:** `SHIPPED`, `DELIVERED`, `DELIVERY_FAILED`, `REFUNDED`, `CLOSED`. Geçişler: `SHIP` (`NEW`→`SHIPPED`), `DELIVER` (`SHIPPED`→`DELIVERED`), `FAIL_DELIVERY` (`SHIPPED`→`DELIVERY_FAILED`), `REFUND_UNDELIVERED` ve `CLOSE_UNDELIVERED` (`DELIVERY_FAILED`'dan; [ADR-077](077-undelivered-order-admin-refund-or-close.md)).
- Gönderinin açılması (`CREATED`) satıcı siparişini değiştirmez: sipariş `NEW` kalır ve iptal edilebilir. `IN_TRANSIT` yalnızca gönderi durumudur. Entegratör ara durumu atlarsa (`CREATED` → `DELIVERED`) satıcı siparişinin geçişleri sırayla uygulanır ve her biri geçmişe yazılır.
- Gönderi durumu sorgusunun yaptığı geçişlerde yapan hesap yoktur: `order_status_changes.actor_account_id` boş geçilebilir olur, boş değer "sistem" demektir. Sahte bir sistem hesabı açılmaz.
- Değişiklik `013-shipments.sql` ile, ekleyici olarak yapılır (CHECK kısıtları genişler, `refunds.reason`'a `UNDELIVERED` eklenir).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Gönderi alanlarını `seller_orders`'a kolon olarak eklemek | Yeniden açılan gönderide eski takip numarası kaybolur; tek etkin gönderi kuralı kolonla ifade edilemez. |
| `READY_TO_SHIP` gibi bir satıcı siparişi durumu | İptal kuralını ikiye böler; bilgi zaten gönderinin varlığından okunur. |
| Her gönderi durumunu satıcı siparişine taşımak | Para ve iptal birimi olan durum makinesi gereksiz şişer. |
| Ayrı `shipment_events` tablosu | Alıcı ve satıcı yalnızca güncel durumu görür; ihtiyaç doğarsa eklenir. |
| Sistem geçişleri için sahte "sistem hesabı" | Hesap tablosunda giriş yapamayan bir satır ve rollerle ilgili istisnalar doğar. |

## Consequences

### Olumlu
- İptal kuralı (ADR-056 §1) değişmeden geçerli; gönderisi açık ama kargoya verilmemiş sipariş iptal edilebilir.
- Teslim zamanı güvenilir biçimde oluşur; ayıplı ürün change'i (48 saat) buna bağlanacak.

### Olumsuz
- Durum sorgusu satıcı paketi şubeye verdikten sonraki ilk tura kadar siparişi `NEW` gösterir; bu pencerede iptal entegratörde reddedilebilir ([ADR-076](076-cancel-cancels-shipment-before-refund.md)).
- Geçmişte boş `actor_account_id` okuyan her kod "sistem" durumunu ayrıca ele almalıdır.

### Nötr / Trade-off
- Kargoya verilme ve teslim zamanı sorgu anıdır, entegratörün olay anı değil; dakika düzeyinde sapma kabul edildi.

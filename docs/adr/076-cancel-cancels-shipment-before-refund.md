# [ADR-076] İptal önce gönderiyi iptal eder, sonra iade; kargoya verilmiş sipariş iptal edilemez

**Tarih:** 2026-10-10
**Durum:** Accepted
**Kaynak:** `add-shipments` design.md D6; [ADR-067](067-cancel-before-shipment-full-refund-restock.md)'yi genişletir
**Not (2026-10-26):** "Olumsuz" bölümündeki ilk madde [ADR-105](105-cancel-refusal-reads-shipment-status-409-when-shipped.md) ile giderildi: entegratör reddedince gönderinin durumu hemen okunur; paket kargoya verilmişse sipariş "Kargoda"ya geçer ve yanıt 409 `ORDER_ALREADY_SHIPPED` olur. Bu ADR'nin kararları geçerlidir.

---

## Context

[ADR-056](056-cancel-before-shipment-and-defect-refund.md) §1 kargoya verilmeden önce iptali tam iade olarak tanımlar. Gönderi açılması siparişi `NEW`'de bırakır ([ADR-072](072-shipments-table-and-seller-order-shipping-states.md)); etiketi basılmış ama şubeye verilmemiş sipariş iptal edilebilmelidir. Para dönüp paketin yola çıkması en kötü sonuçtur.

## Decision

- `OrderCancellationService` geçiş denetiminden sonra, stok ve iadeden önce etkin gönderiyi kilitler ve entegratörde iptal ettirir; gönderi `CANCELLED` olur. Entegratör reddederse 502 `SHIPPING_INTEGRATOR_ERROR` döner ve transaction geri alınır: sipariş, gönderi ve stok değişmez, iade yapılmaz.
- `CANCEL` geçişi yalnızca `NEW`'den izinlidir; `SHIPPED` ve sonrası 409 `ORDER_INVALID_TRANSITION`.
- Entegratörde iptal başarılı olup iade başarısız olursa veritabanı geri alınır ve gönderi bizde `CREATED` görünür; bir sonraki durum sorgusu entegratörden `CANCELLED` okuyup gönderiyi `CANCELLED` yapar ([ADR-075](075-shipment-status-by-scheduled-polling.md)) ve satıcı yeniden gönderi açabilir (yeni referansla).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Gönderi açıldıktan sonra iptali yasaklamak | ADR-056 "kargoya verilmeden önce" diyor; etiket basılmış olması kargoya verilmiş olması değildir. |
| Önce iade, sonra gönderi iptali | İptal başarısız olursa para dönmüş ama paket yola çıkmış olur. |
| Gönderi iptalini transaction dışında, sonradan denemek | Paketin yola çıkma riski ve izlenmesi gereken yeni bir iş. |

## Consequences

### Olumlu
- Para ile paket ayrışmaz: ya ikisi de iptal ya hiçbiri.
- Entegratör ile veritabanı arasındaki olası tutarsızlık durum sorgusuyla kendiliğinden onarılır.

### Olumsuz
- Satıcı paketi şubeye verip durum sorgusu bunu henüz görmemişse alıcının iptali entegratörde reddedilir ve kullanıcı "kargo firmasına ulaşılamadı" hatası görür.
- İptal artık iki dış servise (entegratör, ödeme sağlayıcısı) bağlıdır.

### Nötr / Trade-off
- Hata mesajı nedeni ayırt etmez (ulaşılamadı ile reddetti aynı koddur); kullanıcıya önerilen eylem aynıdır: biraz sonra tekrar denemek.

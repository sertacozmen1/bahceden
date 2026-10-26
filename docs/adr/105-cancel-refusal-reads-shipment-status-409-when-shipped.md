# [ADR-105] Entegratör iptali reddedince gönderinin durumu hemen okunur; paket kargoya verilmişse 409 `ORDER_ALREADY_SHIPPED`

**Tarih:** 2026-10-26
**Durum:** Accepted
**Kaynak:** `change-cancel-rejection-syncs-shipment` design.md D1

---

## Context

İptal önce gönderiyi entegratörde iptal eder, sonra iade yapar. Entegratör reddederse hiçbir şey değişmez ve yanıt 502 `SHIPPING_INTEGRATOR_ERROR` olur ([ADR-076](076-cancel-cancels-shipment-before-refund.md)). Gönderi durumu zamanlanmış sorguyla okunur ([ADR-075](075-shipment-status-by-scheduled-polling.md)). Satıcı paketi şubeye verdikten sonraki ilk sorguya kadar sipariş `NEW` görünür ve iptal düğmesi açıktır. Bu arada iptal isteyen alıcı "kargo firmasına ulaşılamadı, biraz sonra tekrar deneyin" mesajını görür. Tekrar denemek işe yaramaz, çünkü paket yoldadır (`00-current-state.md` L36).

Port, reddin nedenini ayırmaz: "ulaşılamadı" ile "reddetti" aynı istisnadır. Gerçek entegratörün hata biçimi seçim yapılmadan bilinemez (KALANLAR A2).

## Decision

- İptal (alıcı ve satıcı) `TransactionTemplate` içinde yapılır. Entegratör gönderi iptalini reddederse transaction geri alınır: sipariş, gönderi ve stok değişmez, iade yapılmaz (ADR-076 aynen).
- Geri almadan **sonra**, kilitler bırakılmışken, o satıcı siparişinin etkin gönderisinin durumu entegratörden okunur (`ShipmentStatusPoller.refreshActive`). Okunan durum, durum sorgusunun tek gönderi adımıyla uygulanır: aynı geçişler, aynı e-postalar, "sistem" olarak geçmiş satırı.
- Gönderi artık kargoya verilmişse (`HANDED_OVER` ve sonrası) yanıt `409 ORDER_ALREADY_SHIPPED` olur. Mesaj: "Paket kargo firmasına verildi; bu sipariş artık iptal edilemez. Teslim aldıktan sonra sorun bildirebilirsiniz."
- Gönderi hâlâ verilmemiş görünüyorsa veya durum okunamıyorsa (`WARN` log'u) yanıt 502 `SHIPPING_INTEGRATOR_ERROR` kalır. Gönderi bir sonraki turda zaten sorgulanır.

| Alternatif | Neden reddedildi |
|---|---|
| Portta red nedenini ayıran istisna | Gerçek entegratörün hata biçimi bilinmiyor; bilinse de sistemdeki durum eski kalır ve sayfa iptal düğmesini göstermeye devam eder. |
| Yalnızca mesajı değiştirmek ("kargoya verilmiş olabilir") | Belirsiz; ulaşılamama durumunda yanlış bilgi verir, durum eski kalır. |
| Durum sorgusu aralığını kısaltmak | Pencereyi daraltır, kapatmaz; entegratöre yük. |
| Durumu iptal transaction'ı içinde okuyup kaydetmek (`noRollbackFor`) | İptalin "ya hepsi ya hiçbiri" sınırını bulanıklaştırır. Ayrı bir transaction'da yapılsa aynı satırların kilidini bekler. |

## Consequences

### Olumlu
- Alıcıya doğru sebep ve doğru sonraki adım söylenir.
- Sistem aynı istekte gerçeğe gelir: sayfa yenilenince sipariş "Kargoda" ve takip numarasıyla görünür, alıcıya kargo e-postası gider.
- Geçiş kuralları ve e-postalar tek yerde kalır (durum sorgusu).

### Olumsuz
- Red durumunda yanıt bir entegratör çağrısı kadar uzar.
- İptal servisi artık durum sorgusuna bağlıdır (`order` → `shipping`; `shipping` zaten `order`'daki geçiş servisini çağırıyordu).

### Nötr / Trade-off
- İptal denemesi geçmişe yazılmaz (bugün de yazılmıyordu); geçmişte yalnızca "sistem" kargo geçişi görünür.

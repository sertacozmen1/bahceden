# [ADR-091] "Teslim edildi" e-postası: yalnızca alıcıya, geçiş uygulandığında, sorun bildirimi süresiyle

**Tarih:** 2026-10-19
**Durum:** Accepted
**Kaynak:** `add-delivered-email` design.md D1

---

## Context

Alıcı, teslim edilen üründeki sorunu teslimden sonraki 48 saat içinde bildirmek zorunda ([ADR-081](081-defect-claim-opening-window-lines-photos.md)). Bu süre yalnızca sipariş detayında görünüyordu. Paketi kapıda alan ve siteye girmeyen alıcı süreyi kaçırabilir.

[ADR-078](078-shipping-notifications-email-only-three-events.md) "teslim edildi" e-postasını bilerek erteledi: süre bilgisiyle birlikte eklenecekti.

Sipariş e-postaları `OrderNotifier`'da, transaction başarıyla bittikten sonra ve hesabın dilinde gönderilir. Gönderi durumu sorgusu geçişleri `ShipmentProgressService` üzerinden yapar. "Kargoya verildi" ve "teslim edilemedi" e-postaları yalnızca geçiş uygulandığında gider.

## Decision

- `ShipmentProgressService.delivered`, satıcı siparişi `DELIVERED`'a gerçekten geçtiğinde `OrderNotifier.sellerOrderDelivered`'ı çağırır. Geçiş izinli değilse (zaten teslim edilmiş, iptal edilmiş) e-posta gitmez.
- Alıcı yalnızca siparişin alıcısıdır; e-posta hesabın dilindedir. Satıcıya e-posta gitmez.
- İçerik:
  - sipariş numarası ve üreticinin adı;
  - sorun bildirimi için son an ve sürenin saat olarak uzunluğu ([ADR-092](092-email-datetime-turkey-time-fixed-pattern.md));
  - sipariş detayı bağlantısı.
- Son an = geçiş zamanı + `bahceden.claims.window`. Aynı zaman `shipments.delivered_at`'e yazılır ve sipariş detayındaki `claimableUntil` aynı toplamla hesaplanır.
- Gönderim commit sonrasıdır. Hata teslim geçişini geri almaz, yalnızca loglanır ([ADR-019](019-email-via-smtp-provider-agnostic.md)).

| Alternatif | Neden reddedildi |
|---|---|
| Satıcıya da e-posta | Eylem gerektirmeyen bilgi; satıcı paneli yeterli. |
| E-postayı `shipping` paketinde üretmek | Sipariş e-postaları `OrderNotifier`'da; ikinci bir yer metin ve dil mantığını böler. |
| Son anı vermeden "sipariş sayfasından bildirebilirsiniz" demek | Asıl amaç süreyi söylemek. |
| Süre bitmeden hatırlatma e-postası da göndermek | Zamanlanmış ek iş; ihtiyaç ölçülmeden eklenmez (`AGENTS.md` §3). |

## Consequences

### Olumlu
- Alıcı süreli hakkını siteye girmeden öğrenir.
- Paket yolculuğunun dört olayı (kargoya verildi, teslim edildi, teslim edilemedi, admin kararı) e-postayla bildirilir.
- Yinelenen bildirim çift e-posta üretmez; mevcut desen yeterli.

### Olumsuz
- E-postası doğrulanmamış alıcıya da gider (L11).
- Teslim en çok bir sorgu aralığı geç fark edilir. Son an teslim kaydından hesaplandığı için kaymaz, ama alıcının elindeki süre e-posta geldiğinde biraz kısalmıştır.

### Nötr / Trade-off
- Talep süresi ayarı değişirse, gönderilmiş e-postadaki an eski ayarla kalır; sipariş detayı her istekte günceli hesaplar.

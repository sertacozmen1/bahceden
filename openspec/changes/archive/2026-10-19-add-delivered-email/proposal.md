# add-delivered-email

**Durum:** Onaylandı (2026-10-19, proje sahibi), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-19).

> Bu change [`00-current-state.md`](../../../../docs/reference/00-current-state.md) L41 sınırlamasını kapatır. [ADR-078](../../../../docs/adr/078-shipping-notifications-email-only-three-events.md) "teslim edildi" e-postasını bilerek ayıplı ürün süresiyle birlikte eklenmek üzere ertelemişti; o süre artık var ([ADR-081](../../../../docs/adr/081-defect-claim-opening-window-lines-photos.md)). `KALANLAR.md`'deki hiçbir maddeye bağlı değildir. Bildirim kanalı kararı (B4) beklenmez: kargo bildirimleri gibi yalnızca e-posta.

## Why

Alıcı, teslim edilen üründe bir sorun görürse teslimden sonraki **48 saat** içinde bildirmek zorunda ([ADR-081](../../../../docs/adr/081-defect-claim-opening-window-lines-photos.md)). Bugün bu süre yalnızca sipariş detayında görünüyor. Paketi kapıda teslim alan ve siteye girmeyen alıcı süreyi bilmeden kaçırabilir; süre bitince talep açılamaz (`CLAIM_WINDOW_CLOSED`).

"Kargoya verildi" e-postası var, "teslim edildi" yok. Paket yolculuğunun son adımı sessiz kalıyor, alıcının kullanabileceği tek süreli hak da onunla birlikte.

## What Changes

- **`api`:**
  - Gönderi durumu sorgusu satıcı siparişini `DELIVERED`'a geçirdiğinde alıcıya, hesabının dilinde **"siparişiniz teslim edildi"** e-postası gider.
  - E-postada şunlar var:
    - sipariş numarası ve üretici adı;
    - ürünlerde sorun varsa sorun bildirimi için son an, Türkiye saatiyle tarih ve saat olarak (hesabın dilinde biçimlenir) ve kaç saat olduğu;
    - sipariş detayı bağlantısı. Sorun bildir düğmesi orada.
  - E-posta yalnızca geçiş gerçekten uygulandığında gider. Aynı bildirimin ikinci kez gelmesi ikinci e-posta üretmez.
  - Ara durum atlanırsa (`CREATED` → `DELIVERED`) alıcı önce "kargoya verildi", sonra "teslim edildi" e-postasını alır.
  - Gönderim hatası teslim geçişini geri almaz (ADR-019).
  - Tarih-saat biçimi tek bir yardımcıda toplanır (`common/time`); para biçimi `MoneyFormat` ile aynı desen.
- **`web`:** değişmez. Bağlantı mevcut sipariş detayına gider.
- **Sözleşme:** değişmez.

## Capabilities

- **Değişen:** `shipments`. "Gönderi durumu entegratörden izlenir" gereksiniminin "Teslim edildi" ve "Ara durum atlanır" senaryoları e-postayı içerecek biçimde değişir; yinelenen bildirim senaryosu eklenir (delta: `specs/shipments/spec.md`).

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `ShipmentProgressService.delivered` bildirimi çağırır; `OrderNotifier.sellerOrderDelivered`; yeni küçük yardımcı `common/time/DateTimeFormat`; `messages*.properties` (`mail.sellerOrderDelivered.*`); `ShipmentPollingTest` genişler (`critical`); yardımcı için birim testi |
| `web/` | Hayır | - |
| DB | Hayır | Teslim zamanı `shipments.delivered_at`'te zaten var |
| `shared/` | Hayır | - |
| `docs/` | Evet | ADR-091, ADR-092; ADR-078'e genişletme notu; `kargo-ve-teslimat.md` ve `ayipli-urun-ve-iade.md` Tarihçe; `00-current-state.md` (L41 kalkar, test sayısı, sıradaki adım) |

## Bağımlılık

Yeni paket yok (K7). Tarih-saat biçimi `java.time` ile.

## Kişisel veri (K10)

Yeni bir kişisel veri alanı yok. E-posta alıcının kendi adresine gider ve yalnızca sipariş numarası, üretici adı, süre ve bağlantı içerir; adres ve takip numarası yoktur. Log'a yalnızca sipariş numarası yazılır.

## Non-goals

- Satıcıya "teslim edildi" e-postası. Satıcı durumu panelde görür; para etkisi yok.
- Süre bitmeden önce hatırlatma e-postası. Zamanlanmış ek bir iş gerektirir; ihtiyaç ölçülünce ayrı change.
- SMS veya push (KALANLAR B4).
- Teslim edildi e-postasından doğrudan talep formuna giden bağlantı. Sipariş detayı birden fazla satıcı siparişini gösterir; talep orada satıcı siparişi seçilerek açılır.
- E-postası doğrulanmamış alıcıya gönderimi engellemek (L11 ayrı change).

## Riskler

- **E-posta gecikmesi:** teslim, durum sorgusunun aralığı kadar (5 dakika) geç fark edilir; e-postadaki son an teslim kaydından hesaplandığı için doğrudur, gecikme süreyi kısaltmaz.
- **Sahte entegratör** teslimi zamanla üretir. Geliştirmede bu e-posta, gönderi açıldıktan birkaç dakika sonra mailpit'e düşer; testte sabit saatle tetiklenir.

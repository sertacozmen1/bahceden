# [ADR-071] `shipping` paketi; entegratör dört metotlu bir arayüz, tek uygulama `fake`

**Tarih:** 2026-10-10
**Durum:** Accepted
**Kaynak:** `add-shipments` design.md D1

---

## Context

[ADR-057](057-shipping-via-integrator-port-integrator-pending.md) gönderinin bir kargo entegratörü API'siyle açılmasına, entegratörün bir arayüzün arkasında durmasına ve seçimin sonraya kalmasına karar verdi (KALANLAR A2). Ödeme tarafında aynı durum `PaymentProvider` arayüzü ve `FakePaymentProvider` ile çözüldü ([ADR-054](054-split-payment-via-provider-port-provider-pending.md)). Geliştirme ve testte gerçek gönderi açmak yasak (`AGENTS.md` K10). `AGENTS.md` §11.2 `shipping` domainini zaten sayıyor.

## Decision

- `com.bahceden.shipping` paketi açılır: `Shipment` entity'si, `ShipmentStatus` ve `PackageSize` enum'ları, `ShipmentRepository`, `ShipmentService`, `ShipmentStatusPoller`, `SellerShipmentController`.
- `ShippingIntegrator` arayüzü dört metottur: `create(ShipmentRequest)`, `label(ref)`, `status(ref)`, `cancel(ref)`. Ulaşılamazlık veya ret `ShippingIntegratorException` fırlatır; servis bunu `SHIPPING_INTEGRATOR_ERROR` (502) yanıtına çevirir ve transaction geri alınır.
- Tek uygulama `FakeShippingIntegrator`: ağ çağrısı yapmaz; gönderinin açılış anı anahtarın içindedir, durum açılıştan geçen süreyle yapılandırılmış eşiklere göre hesaplanır (yeniden başlatmada ilerleme sürer).
- Entegratör yapılandırmada adla seçilir (`bahceden.shipping.integrator`); bugün geçerli tek değer `fake`, başka değerle uygulama açılmaz.
- **Paketler arası yön:** `shipping`, `order`'ın repository'sini okur ve satıcı siparişi geçişlerini `order` paketindeki `ShipmentProgressService` ile yaptırır; `order`, `shipping`'i yalnızca iptalde gönderiyi iptal ettirmek ve görünümlere gönderi bilgisini eklemek için çağırır. Geçiş mantığı ve e-postalar `order`'da kalır. Spring bean döngüsü yoktur.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Gönderiyi `order` paketine koymak | §11.2 domain ayrımına aykırı; entegratör kodu siparişle iç içe geçer. |
| Arayüzü genişletmek (adres doğrulama, fiyat sorgulama, toplu gönderi) | Bugün ihtiyaç yok (§3). |
| Durumu bellekte tutan sahte entegratör | Yeniden başlatmada açık gönderiler takılı kalır; anahtara gömülü açılış anı yeterli. |

## Consequences

### Olumlu
- Gerçek entegratör seçildiğinde yalnızca yeni bir uygulama ve yapılandırma değeri eklenir.
- Testler ayrı bir test uygulamasıyla (`ScriptedShippingIntegrator`) "teslim edilemedi", ara durum atlama ve hata dallarını sınar.

### Olumsuz
- `shipping` ile `order` arasında iki yönlü paket bağımlılığı vardır (bean düzeyinde döngü yok); sorumluluk sınırı yorumla ve bu ADR'yle korunur.
- Sahte entegratör "teslim edilemedi" üretmez; bu dal geliştirme ortamında elle denenemez.

### Nötr / Trade-off
- Sahte entegratörün iptal kayıtları bellektedir; yeniden başlatmada unutulur. Sahte entegratör için kabul edildi.

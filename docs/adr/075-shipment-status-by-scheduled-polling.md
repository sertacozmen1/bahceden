# [ADR-075] Gönderi durumu zamanlanmış sorguyla alınır; sahte entegratör zamanla ilerler

**Tarih:** 2026-10-10
**Durum:** Accepted
**Kaynak:** `add-shipments` design.md D5

---

## Context

ADR-057 §2 gönderi durumunun entegratörden geleceğini, alma yönteminin (bildirim veya periyodik sorgu) entegratör seçilince belirleneceğini söyler. Entegratör seçilmedi (KALANLAR A2). Teslim zamanı, ayıplı ürün süresinin (ADR-056 §4) başlangıcıdır; kimse ekrana bakmasa da oluşmalıdır. MVP tek sunucuda çalışır (§3).

## Decision

- Spring'in kendi zamanlayıcısı (`@EnableScheduling`, `@Scheduled`) kullanılır; yeni paket yoktur. `ShipmentStatusPoller` her turda `CREATED` / `HANDED_OVER` / `IN_TRANSIT` gönderilerden `last_polled_at`'i en eski olan `pollBatchSize` kadarını (varsayılan 100) alır; aralık `pollInterval` (varsayılan 5 dk, dev 1 dk).
- Her gönderi kendi transaction'ında kilitlenir, entegratöre sorulur ve [ADR-072](072-shipments-table-and-seller-order-shipping-states.md)'deki geçişler uygulanır. Bir gönderinin hatası `WARN` ile (gönderi kimliğiyle, takip numarasıyla değil) loglanır, gönderi sıranın sonuna geçer ve döngü devam eder.
- Entegratörün `CANCELLED` bildirimi `CREATED` gönderiyi `CANCELLED` yapar (satıcı siparişine yansımaz): iptal sırasında entegratörde iptal edilip veritabanında geri alınan gönderi böyle onarılır ([ADR-076](076-cancel-cancels-shipment-before-refund.md)).
- `bahceden.shipping.polling-enabled=false` zamanlayıcıyı hiç kurmaz; testler sorguyu sabit saatle doğrudan çağırır.
- Sahte entegratör durumu açılıştan geçen süreyle ilerletir; süreler yapılandırmadadır (temel: 6 / 12 / 36 saat; dev: 2 / 4 / 6 dakika) ve sıralı olmaları açılışta doğrulanır.
- Tek örnek varsayılır; iki örnek aynı gönderiyi işlese de satır kilidi ve "geri gidiş yok sayılır" kuralı çift geçişi önler.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Webhook | Entegratör seçilmeden biçimi ve imzası belli değil; sahte entegratör için dışarıya uç açmak gereksiz. Gerçek entegratör sunarsa eklenir, sorgu yedek kalır. |
| Durumu yalnızca ekran açıldığında sorgulamak | Kimse bakmazsa teslim tarihi oluşmaz. |
| Geliştirmede admin'e "durumu ilerlet" ucu | Üretimde bulunmaması gereken bir uç; zamanla ilerleyen sahte entegratör aynı ihtiyacı karşılar. |
| Kuyruk veya iş zamanlayıcı kütüphanesi (Quartz vb.) | §3 ve K7: tek sunucuda gereksiz. |

## Consequences

### Olumlu
- Teslim zamanı ve "kargoya verildi" e-postası kendiliğinden oluşur.
- Geliştirmede akış birkaç dakikada uçtan uca izlenebilir.

### Olumsuz
- Durum en çok bir sorgu aralığı kadar gecikir.
- Çok örnekli kurulumda iş tekrar eder (L9 ile aynı sınır); dağıtık kilit gerekirse ayrı karar.
- "Teslim edilemedi" geliştirme ortamında elle denenemez; testlerde sınanır.

### Nötr / Trade-off
- Sorgu yükü açık gönderi sayısıyla doğru orantılıdır; parti boyutu ve aralık yapılandırmadadır.

# Design: add-shipments

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altında bir ADR olur (`AGENTS.md` K1 / §5.2). Eşleşme:
>
> | Karar | ADR |
> |---|---|
> | D1 | ADR-071 |
> | D2 | ADR-072 |
> | D3 | ADR-073 |
> | D4 | ADR-074 |
> | D5 | ADR-075 |
> | D6 | ADR-076 |
> | D7 | ADR-077 |
> | D8 | ADR-078 |
>
> Aşağıdaki kararlar bu çerçeve kararlarının uygulama ayrıntılarıdır ve hiçbirine aykırı değildir:
>
> | ADR | Çerçeve |
> |---|---|
> | [ADR-057](../../../docs/adr/057-shipping-via-integrator-port-integrator-pending.md) | Gönderi entegratör API'siyle, arayüz arkasında |
> | [ADR-056](../../../docs/adr/056-cancel-before-shipment-and-defect-refund.md) | Kargodan önce iptal tam iade |
> | [ADR-058](../../../docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md) | Ücret ve kargoya verme süresi |
> | [ADR-045](../../../docs/adr/045-seller-documents-table-content-sniffing-protected-serving.md) | Kişisel veri içeren dosyanın korumalı sunumu |
> | [ADR-039](../../../docs/adr/039-uploads-on-local-volume-served-by-api.md) | Dosyalar yerel volume'da |
> | [ADR-007](../../../docs/adr/007-query-level-ownership-scoping.md) | Sorgu seviyesinde sahiplik |
> | [ADR-066](../../../docs/adr/066-payment-redirect-verify-lock-stock-on-success.md), [ADR-067](../../../docs/adr/067-cancel-before-shipment-full-refund-restock.md) | Ödeme ve iptal akışı |

## Kararlar

### D1 · `shipping` paketi; entegratör dört metotlu bir arayüz, tek uygulama `fake`

`com.bahceden.shipping` paketi açılır (§11.2 listesinde zaten var). İçinde:

- `ShippingIntegrator` arayüzü: `create(ShipmentRequest)`, `label(String integratorRef)`, `status(String integratorRef)`, `cancel(String integratorRef)`. Her metot ulaşılamazlık veya red durumunda `ShippingIntegratorException` fırlatır (ödeme tarafındaki `PaymentProviderException` deseni).
- `FakeShippingIntegrator`: ağ çağrısı yapmaz; durumu açılış zamanından itibaren yapılandırılmış sürelerle hesaplar (D5); etiket görselini JDK Java2D + ImageIO ile PNG olarak üretir (D4).
- `Shipment` entity'si, `ShipmentStatus`, `PackageSize` enum'ları, `ShipmentRepository`, `ShipmentService`, `ShipmentStatusPoller`, `SellerShipmentController`, `AdminSellerOrderController`.
- `BahcedenProperties.shipping`: `integrator` (`@Pattern("fake")`, açılışta doğrulanır), `pollInterval`, `pollBatchSize`, `pollingEnabled`, `fake.handedOverAfter`, `fake.inTransitAfter`, `fake.deliveredAfter`, `packageDesi` (boyut → desi eşlemesi).

**Paketler arası yön:** `shipping` `order`'ı çağırır (satıcı siparişini okur, geçiş uygular); `order` `shipping`'i yalnızca iptalde gönderiyi iptal ettirmek ve görünümlere gönderi bilgisini eklemek için çağırır. Döngüyü önlemek için `order` tarafı `shipping`'in yalnızca `ShipmentService`'inin dar bir yüzünü (`cancelActive(sellerOrderId)`, `findViewsFor(sellerOrderIds)`) kullanır; geçiş mantığı `order` içinde `SellerOrderService`'te kalır ve `shipping` onu çağırır. Spring bean döngüsü olmaması `@SpringBootTest` açılışıyla sabitlenir.

**Gerekçe:** ADR-057 §3'ün doğrudan uygulaması; ödeme tarafıyla aynı biçim, gerçek entegratör seçildiğinde yalnızca yeni bir uygulama eklenir.

**Reddedilen alternatifler:**
- (a) Gönderiyi `order` paketine koymak: §11.2 domain ayrımına aykırı; entegratör kodu siparişle iç içe geçer.
- (b) Arayüzü genişletmek (adres doğrulama, fiyat sorgulama, toplu gönderi): bugün ihtiyaç yok (§3).

### D2 · Gönderi ayrı tabloda; satıcı siparişi durumları genişler, ara gönderi durumları satıcı siparişine yansımaz

**`shipments` tablosu:** `seller_order_id`, `integrator` (`fake`), `integrator_ref`, `carrier` (firma adı), `tracking_number`, `package_size`, `status`, `label_key` (depolama anahtarı), `label_content_type`, `handed_over_at`, `delivered_at`, `last_polled_at`, `created_at`, `updated_at`. İptal edilen gönderi silinmez (§11.4); bir satıcı siparişinin tek **etkin** gönderisi kısmi benzersiz indeksle zorunlu: `unique (seller_order_id) where status <> 'CANCELLED'`.

**`ShipmentStatus`:** `CREATED`, `HANDED_OVER`, `IN_TRANSIT`, `DELIVERED`, `DELIVERY_FAILED`, `CANCELLED`. Sıralı ilerler; geri giden bildirim yok sayılır.

**`SellerOrderStatus` genişler:** `SHIPPED`, `DELIVERED`, `DELIVERY_FAILED`, `REFUNDED`, `CLOSED`. `SellerOrderTransition`'a eklenir:

| Geçiş | Kaynak | Hedef |
|---|---|---|
| `SHIP` | `NEW` | `SHIPPED` |
| `DELIVER` | `SHIPPED` | `DELIVERED` |
| `FAIL_DELIVERY` | `SHIPPED` | `DELIVERY_FAILED` |
| `REFUND_UNDELIVERED` | `DELIVERY_FAILED` | `REFUNDED` |
| `CLOSE_UNDELIVERED` | `DELIVERY_FAILED` | `CLOSED` |

Gönderi açılması (`CREATED`) satıcı siparişinin durumunu değiştirmez; sipariş `NEW` kalır, böylece "kargoya verilmeden önce iptal" kuralı (ADR-056 §1) olduğu gibi geçerlidir. `IN_TRANSIT` yalnızca gönderi durumudur. Entegratör ara durumu atlarsa (örn. `CREATED` → `DELIVERED`) geçişler sırayla uygulanır ve her biri geçmişe yazılır. `order_status_changes.actor_account_id` bugün `not null`; sistem (poller) geçişlerinde yapan hesap yoktur, bu yüzden `013` kolonu boş geçilebilir yapar ve `actor` boşsa "sistem" okunur. Sahte bir "sistem hesabı" açılmaz.

**Gerekçe:** Satıcı siparişi para ve iptal birimidir; gönderinin her ara durumunu ona taşımak durum makinesini şişirir. Gönderi ayrı kayıt olduğu için iptal edilip yeniden açılan gönderinin izi kalır.

**Reddedilen alternatifler:**
- (a) Gönderi alanlarını `seller_orders`'a kolon olarak eklemek: yeniden açılan gönderide eski takip numarası kaybolur; tek etkin gönderi kuralı kolonla ifade edilemez.
- (b) `READY_TO_SHIP` gibi bir satıcı siparişi durumu: iptal kuralını ikiye böler; bilgi zaten gönderinin varlığından okunur.
- (c) Ayrı `shipment_events` tablosu: alıcı ve satıcı yalnızca güncel durumu görür; ihtiyaç doğarsa eklenir.

### D3 · Gönderi açma: koli boyutu seçilir, gönderici satıcının adı-ili-telefonu, şubeye teslim; istek tekrarlanabilir

`POST /api/v1/sellers/me/orders/{id}/shipment` gövdesi `{ "packageSize": "MEDIUM" }`. Akış tek transaction'da:

1. `requireApprovedSeller`; satıcı siparişi `findForSellerUpdate(id, sellerId)` ile **kilitli** okunur (`SELECT … FOR UPDATE`; sahiplik sorguda, ADR-007).
2. Etkin gönderi varsa o döner (200). Durum `NEW` değilse 409 `ORDER_INVALID_TRANSITION`. Satıcının telefonu yoksa 422 `SHIPMENT_SENDER_PHONE_MISSING` ve entegratör çağrılmaz.
3. `create` çağrılır: gönderici (görünen ad, il, telefon), alıcı (sipariş adresinin anlık kopyası), koli boyutu → desi (yapılandırmadan), referans `BHC-<sellerOrderId>-<n>` (n = o satıcı siparişinin gönderi sayısı + 1; gerçek entegratörlerin tekrar korumasına uygun).
4. Etiket `label` ile alınır ve depolamaya yazılır (D4); gönderi `CREATED` olarak kaydedilir (201).

Entegratör hatası 502 `SHIPPING_INTEGRATOR_ERROR` döner ve hiçbir şey kaydedilmez. Entegratörde açılıp veritabanına yazılamayan gönderi riski Risks bölümünde.

**Koli boyutu** `SMALL` / `MEDIUM` / `LARGE`, desi karşılığı yapılandırmada (varsayılan 5 / 15 / 30). Satıcıdan serbest desi istenmez: sahada telefonla çalışan satıcı için üç seçenek yeterli (Hick, §12.5). Ücreti etkilemez (ADR-058).

**Gerekçe:** Ürün sahibinin şubeye teslim kararı; satıcıdan yeni kişisel veri (açık adres) toplanmaz. Kilit, iki sekmeden aynı anda basılan "kargoya hazırla"nın iki gönderi açmasını önler; kısmi benzersiz indeks ikinci savunma hattıdır.

**Reddedilen alternatifler:**
- (a) Satıcının açık adresi ve kapıdan alım: ürün sahibi reddetti (2026-10-10).
- (b) Serbest desi alanı: hata payı yüksek; ücreti etkilemediği için hassasiyet gereksiz.
- (c) Gönderiyi ödeme anında otomatik açmak: satıcı ürünü hazırlamadan etiket oluşur; iptal edilen siparişlerde boş gönderi birikir.

### D4 · Etiket depolamada; yalnızca sahibine korumalı başlıklarla; panelde gönderi kodu büyük puntoyla

Etiket `FileStorage` ile `labels/<rastgele-anahtar>` altına yazılır (ADR-039). İçerik türü entegratörden gelir ve beyaz listededir (`application/pdf`, `image/png`); başka tür 502 olarak ele alınır.

`GET /api/v1/sellers/me/orders/{id}/shipment/label` yalnızca sahibine (sorgu seviyesinde) döner; başlıklar ADR-045 ile aynıdır: `nosniff`, `Content-Security-Policy: sandbox`, `Cache-Control: no-store`, `Content-Disposition: inline`. `web`, etiketi `/api/seller/orders/[id]/label` Route Handler'ından aktarır (satıcı belgesi aktarma deseni).

Panelde "Etiketi aç ve yazdır" bağlantısı etiketi yeni sekmede açar; yazdırma tarayıcının görüntüleyicisinden yapılır. Sayfa içi önizleme yoktur: `Content-Security-Policy: sandbox` başlığı tarayıcının PDF görüntüleyicisini çerçeve içinde engeller ve bu koruma gevşetilmez. Yazıcısı olmayan satıcı için takip numarası **gönderi kodu** olarak büyük ve kopyalanabilir gösterilir: şubede kodla gönderi kabulü çoğu entegratörde mümkündür; gerçek entegratör seçildiğinde metin buna göre uyarlanır.

`fake` etiket, yazı tipi dosyası gerektirmeyen, elle üretilmiş tek sayfalık bir PDF'tir (PDF'in yerleşik Helvetica'sı, yalnızca ASCII): "ORNEK ETIKET - GECERSIZ" başlığı, kargo firması ve referans. Kişisel veri ve barkod içermez. Java2D ile PNG çizmek reddedildi: `api` çalışma imajı (Alpine) yazı tipi içermez ve metin çizimi orada hata verir; imaja yazı tipi paketi eklemek bağımlılık kararıdır (K7).

**Gerekçe:** Etiket alıcının adını, telefonunu ve adresini içerir (K10); belge sunumundaki koruma burada da gerekir. Ek kütüphane yok (K7).

**Reddedilen alternatifler:**
- (a) Etiketi veritabanında `bytea` saklamak: dosyalar için karar ADR-039'da verildi.
- (b) Etiketi her istekte entegratörden çekmek: entegratöre bağımlılık ve gecikme; bazı entegratörler etiketi bir kez verir.
- (c) Barkodlu sahte etiket: ek kod veya kütüphane; `fake` için değeri yok.
- (d) Etiketi sayfa içinde `<iframe>` ile önizlemek: `sandbox` başlığıyla tarayıcı PDF'i çerçevede göstermez; başlığı gevşetmek ADR-045'in korumasını bozar.

### D5 · Durum zamanlanmış sorguyla alınır; `fake` zamana göre ilerler

`@EnableScheduling` açılır (Spring çekirdeği, yeni paket değil). `ShipmentStatusPoller` `pollInterval` aralıkla (varsayılan 5 dk, dev 1 dk) `CREATED` / `HANDED_OVER` / `IN_TRANSIT` gönderilerden `last_polled_at`'i en eski olan `pollBatchSize` kadarını (varsayılan 100) alır. Her gönderi ayrı transaction'da (`TransactionTemplate`) kilitli okunur, `status` sorulur ve D2'deki geçişler uygulanır. Bir gönderinin hatası `WARN` ile loglanır (takip numarası değil, gönderi kimliğiyle) ve döngü devam eder. `pollingEnabled=false` testlerde zamanlayıcıyı kapatır; testler poller metodunu sabit `Clock` ile doğrudan çağırır.

`fake`: `now - createdAt` ≥ `handedOverAfter` → `HANDED_OVER`; ≥ `inTransitAfter` → `IN_TRANSIT`; ≥ `deliveredAfter` → `DELIVERED` (dev varsayılanları 2 / 4 / 6 dk; temel yapılandırmada saatler). `DELIVERY_FAILED` ve entegratör hataları yalnızca testlerde, test uygulamasıyla sınanır (L28'deki ödeme deseni).

Tek örnek varsayılır (§3, L9 ile aynı); iki örnek aynı gönderiyi aynı anda işlerse satır kilidi ve "geri gidiş yok sayılır" kuralı çift geçişi önler.

**Gerekçe:** ADR-057 alma yöntemini entegratöre bıraktı; sorgu her entegratörde mümkündür, webhook ise imza doğrulaması ve dışarıya açık uç gerektirir. Gerçek entegratör webhook sunarsa poller yedek olarak kalabilir.

**Reddedilen alternatifler:**
- (a) Webhook: entegratör seçilmeden biçimi belli değil; `fake` için dışarıya uç açmak gereksiz.
- (b) Durumu yalnızca ekran açıldığında sorgulamak: kimse bakmazsa teslim tarihi oluşmaz; ayıplı ürün süresi buna bağlanacak.
- (c) Dev ortamında admin'e "durumu ilerlet" ucu: üretimde bulunmaması gereken bir uç; zamanla ilerleyen `fake` aynı ihtiyacı karşılar.
- (d) Kuyruk / iş zamanlayıcı kütüphanesi (Quartz vb.): §3 ve K7.

### D6 · İptal önce gönderiyi iptal eder, sonra iade; kargoya verilmiş sipariş iptal edilemez

`OrderCancellationService.cancel` geçiş kontrolünden sonra, stok ve iade adımlarından önce `ShipmentService.cancelActive(sellerOrderId)` çağırır: etkin gönderi `CREATED` ise entegratörde `cancel` edilir ve `CANCELLED` olur. Entegratör reddederse (örn. paket fiilen şubeye verilmiş ama poller henüz görmemiş) 502 `SHIPPING_INTEGRATOR_ERROR` döner ve transaction geri alınır. `CANCEL` geçişi yalnızca `NEW`'den izinli olduğu için `SHIPPED` ve sonrası zaten 409 döner.

**Sıra gerekçesi:** Önce gönderi iptal edilir; iade önce yapılıp gönderi iptali başarısız olsaydı para dönmüş ama paket yola çıkmış olurdu. Ters durumda (gönderi entegratörde iptal edildi, iade başarısız, veritabanı geri alındı) gönderi bizde `CREATED` görünür ama entegratörde iptaldir; poller bunu `CANCELLED` olarak okuyunca gönderiyi `CANCELLED` yapar ve satıcı yeniden açabilir. Bu yüzden poller `CANCELLED` bildirimini de işler (satıcı siparişine yansımaz).

**Reddedilen alternatifler:**
- (a) Gönderi açıldıktan sonra iptali yasaklamak: ADR-056 §1 "kargoya verilmeden önce" diyor; etiket basılmış olması kargoya verilmiş olması değildir.
- (b) Gönderi iptalini transaction dışında, sonradan denemek: paketin yola çıkma riski.

### D7 · Teslim edilemeyen sipariş: admin `REFUND` veya `CLOSE`; stok geri eklenmez

Ürün sahibinin kararı (2026-10-10). `GET /api/v1/admin/seller-orders?status=DELIVERY_FAILED` (sayfalı, ortak liste şekli; `status` beyaz listesi `DELIVERY_FAILED` (varsayılan, karar bekleyen), `REFUNDED`, `CLOSED`: admin verdiği kararları da izler), `GET /api/v1/admin/seller-orders/{id}` ve `POST /api/v1/admin/seller-orders/{id}/resolution` `{ "decision": "REFUND" | "CLOSE", "reason": "…" }`.

- `REFUND`: satıcı siparişi kilitlenir, `REFUND_UNDELIVERED` geçişi, `PaymentService.refund(..., RefundReason.UNDELIVERED, refundableAmount(), subMerchantKey)`; iade başarısızsa 502 ve geri alma. Stok geri eklenmez: taze ürün dönüşte satılabilir sayılmaz; satıcı isterse stoğu kendisi artırır.
- `CLOSE`: gerekçe zorunlu (en az 10 karakter, satıcı iptal gerekçesiyle aynı ölçüt); para hareketi yok, satıcı hakedişi geçerli kalır.
- Gerekçe `REFUND`'da isteğe bağlıdır. İkisi de geçmişe admin hesabıyla yazılır; alıcıya ve satıcıya e-posta gider.
- Admin detayı: satırlar, tutarlar, satıcı, gönderi bilgisi, alıcının adı ve telefonu (iletişim için); açık adres yok.

`web`: `/admin/orders` liste + detay diyaloğu, mevcut admin ekranlarının (`/admin/products` gerekçe diyaloğu) deseniyle; admin gezinmesine bağlantı eklenir.

**Gerekçe:** Teslim edilemeyişin sebebi (yanlış adres, alıcı yok, kargo hatası) sistemden okunamaz; taze ürünün geri dönüşte durumu da belirsiz. Otomatik kural yerine admin hakemliği ADR-056 §4'teki anlaşmazlık modeliyle tutarlı.

**Reddedilen alternatifler:**
- (a) Otomatik tam iade: ürün sahibi reddetti; satıcı alıcı hatasının bedelini öder.
- (b) Kısmi iade seçeneği: ayıplı ürün change'inin konusu (ADR-056 §5); burada yalnızca iki uç karar.
- (c) Satıcıya karar yetkisi: çıkar çatışması.

### D8 · Bildirimler: yalnızca e-posta, üç olay

`OrderNotifier` genişler (yeni sınıf değil, K9): alıcıya "kargoya verildi" (firma, takip numarası); alıcıya ve satıcıya "teslim edilemedi"; admin kararında alıcıya ve satıcıya karar e-postası. Hesap dilinde (ADR-032), transaction sonrasında; gönderim hatası işlemi geri almaz. "Teslim edildi" e-postası yok (ayıplı ürün change'i bu e-postayı 48 saat bilgisiyle ekleyecek). Takip numarası e-postaya girer (alıcının kendi verisi) ama uygulama log'una girmez (K10).

**Reddedilen alternatifler:**
- (a) Her gönderi durumunda e-posta: gürültü; alıcı sipariş sayfasından izler.
- (b) SMS: KALANLAR B4.

## Risks / Trade-offs

- **Entegratörde açılan gönderi veritabanına yazılamazsa** (D3): `fake`'te etkisiz. Gerçek entegratörde referans `BHC-<sellerOrderId>-<n>` ile tekrar korumalı istek ve entegratör tarafındaki yetim gönderilerin iptali gerçek uygulamanın işi olur; Open Questions'ta.
- **Poller tek örnek varsayar** (D5): iki örnekte aynı gönderi iki kez sorgulanır ama satır kilidi çift geçişi önler. L9 ile birlikte `00-current-state.md`'ye yazılır.
- **Durum gecikmesi:** satıcı paketi şubeye verdiği andan sonraki ilk sorguya kadar alıcı siparişi `NEW` görür ve iptal edebilir; bu pencerede iptal entegratör tarafından reddedilir (D6, 502). Kullanıcıya gösterilen metin "kargo firmasına ulaşılamadı, biraz sonra tekrar deneyin" olur.
- **Etiketler silinmiyor:** saklama süresi KALANLAR A5'e eklenir (L24 ile aynı konu).
- **`DELIVERY_FAILED` dev'de elle denenemez:** testlerde sınanır.
- **Satıcının hakedişi teslimde serbest bırakılmıyor:** gerçek sağlayıcıda (örn. alt üye ödemesinin onayı) gerekebilir; KALANLAR A1 seçimine bağlı.

## Migration Plan

1. **`013-shipments.sql`** (`--rollback` satırlarıyla):
   - `shipments` tablosu, yabancı anahtar `seller_orders(id)`, durum / boyut CHECK kısıtları, `unique (seller_order_id) where status <> 'CANCELLED'`, `shipments(status, last_polled_at)` indeksi, `check (status <> 'DELIVERED' or delivered_at is not null)`.
   - `seller_orders_status_check`, `order_status_changes_from_check`, `order_status_changes_to_check` yeni değerlerle yeniden kurulur (drop + add); `refunds_reason_check`'e `UNDELIVERED` eklenir. `order_status_changes.actor_account_id` için `drop not null` (rollback: sistem satırı yokken `set not null`).
   - Ekleyicidir; mevcut satırlar yeni kısıtları zaten sağlar. Rollback yeni değerleri taşıyan satır yokken eski kısıtları geri kurar.
2. **Dev seed:** yeni seed yok. Hesaplı dev satıcısının telefonu `010` seed'inde zaten var; dev'de gönderi panelden açılır ve `fake` birkaç dakikada ilerletir.
3. Canlı veri yok.

## Open Questions

- **Gerçek entegratörün tekrar koruması ve yetim gönderiler:** referans alanının entegratörde benzersizlik garantisi olup olmadığı seçimle netleşir (KALANLAR A2).
- **Şubede kodla kabul:** etiket basmadan gönderi kodu ile kabul entegratöre ve firmaya bağlı; panel metni gerçek entegratörle güncellenir.
- **Hakedişin teslimde serbest bırakılması:** sağlayıcıya bağlı (KALANLAR A1).
- **Etiket saklama süresi:** KALANLAR A5'e eklenir.

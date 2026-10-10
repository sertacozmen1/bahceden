# Tasks: add-shipments

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7): zamanlama Spring `@Scheduled`, etiket görseli JDK Java2D/ImageIO.
> Şema değişikliği ekleyicidir: yeni tablo, genişleyen CHECK kısıtları, bir kolonda `drop not null`; veri kaybı yok.
> Dış servise kişisel veri gönderimi ADR-057 §4 ile onaylı; entegratör `fake` olduğu için bu change'te dışarı veri gitmez.

## Faz 0 · Ürün belgesi ve feature kaydı

- [x] 0.1 `docs/product/kargo-ve-teslimat.md` şablondan yazılır ve `docs/product/index.md`'ye eklenir: kullanıcı problemi (sipariş "Hazırlanıyor"da kalıyor), değer önerisi, rakip analizi (büyük pazaryerlerinde satıcı kargo akışı, şubeye teslim, gönderi kodu), kapsam ve kabul kriterleri (spec senaryolarıyla eşleşir), başarı metrikleri (ödemeden kargoya verilmeye süre, süre aşımı oranı, teslim edilemeyen oranı, gönderi iptal oranı).
- [x] 0.2 `docs/features/kargo-ve-teslimat.md` (Durum: Geliştirmede) açılır, `docs/features/index.md` tablosuna eklenir.

## Faz 1 · Gönderi açma ve etiket: şema → `shipping` → sözleşme → satıcı paneli

Bu faz bittiğinde satıcı gönderi açar, etiketi görür ve gönderi kodunu okur; durum henüz ilerlemez.

> **Uygulama notu (2026-10-10):** Fazlar aynı oturumda art arda uygulandı; faz 1 için öngörülen geçici "gönderisi açık siparişin iptali 409" kuralı yazılmadı, iptal doğrudan D6'ya göre (önce gönderi iptali) kuruldu. Sapmalar: sahte etiket PNG değil, yazı tipi gerektirmeyen elle üretilmiş PDF (çalışma imajı Alpine'de yazı tipi yok; design D4 güncellendi); etiket sayfa içinde önizlenmiyor, yeni sekmede açılıp oradan yazdırılıyor (`CSP: sandbox` tarayıcının PDF görüntüleyicisini çerçeve içinde engeller); admin listesi `DELIVERY_FAILED` yanında karara bağlanmış `REFUNDED` / `CLOSED` sekmelerini de gösteriyor (design D7 güncellendi). 2.3'teki "geçmiş görünümü" `api`'de dışa açık bir geçmiş ucu olmadığı için yalnızca veri düzeyinde (boş `actor_account_id` = sistem) uygulandı.

- [x] 1.1 `013-shipments.sql` design.md Migration Plan 1'e göre (`--rollback` dahil) yazılır ve master changelog'a eklenir. Bütün şema değişikliği bu changeset'tedir.
- [x] 1.2 `shipping` paketi: `Shipment` entity'si, `ShipmentStatus`, `PackageSize`, `ShipmentRepository` (`findActiveBySellerOrderId`, sahiplik sorgulu `findActiveForSeller(sellerOrderId, sellerId)`), `ShippingIntegrator` arayüzü + `ShippingIntegratorException`, `FakeShippingIntegrator` (zaman tabanlı durum, PNG etiket), `BahcedenProperties.shipping` (açılışta doğrulanır; `application*.yml` ve `api/.env.example` gerekiyorsa).
- [x] 1.3 `SellerOrderStatus` ve `SellerOrderTransition` D2'deki değer ve geçişlerle genişler; `SellerOrderRepository.findForSellerUpdate` (kilitli, sahiplik sorgulu).
- [x] 1.4 `ShipmentService.create` (D3): onaylı satıcı kapısı, kilit, tekrar isteğinde mevcut gönderi, telefon kontrolü, entegratör çağrısı, etiketin `FileStorage`'a yazılması, içerik türü beyaz listesi. Log'da takip numarası ve adres yok.
- [x] 1.5 DTO'lar `@Schema` açıklaması ve örneğiyle: `ShipmentRequest` (`packageSize`), `ShipmentView` (firma, takip numarası, durum, koli boyutu, kargoya verilme / teslim zamanı, etiket yolu yalnızca satıcı görünümünde). `SellerShipmentController`: `POST …/shipment`, `GET …/shipment/label` (ADR-045 başlıkları). `SecurityConfig`, yeni `ApiErrorCode` değerleri (`SHIPMENT_SENDER_PHONE_MISSING`, `SHIPPING_INTEGRATOR_ERROR`), `messages*.properties`.
- [x] 1.6 `SellerOrderDetail`'e `shipment` alanı; satıcı listesi durum süzgecinin beyaz listesi yeni durumlarla genişler.
- [x] 1.7 `api` testleri (`critical`): gönderi açılır; tekrar istek yeni gönderi açmaz; eşzamanlı iki istek tek gönderi; başka satıcı 404; onaysız satıcı 403; oturumsuz 401; `NEW` olmayan durum 409; koli boyutu doğrulaması 400; telefon yok 422 ve entegratör çağrılmaz; entegratör hatası 502 ve kayıt yok; etiket başlıkları; başkasının etiketi 404. `FakeShippingIntegrator` birim testi (süreler, etiket PNG okunabilir). `MockMvc` sözleşme testleri.
- [x] 1.8 `openapi.yaml` yeniden üretilir (`OpenApiContractTest` yeşil); `npm --prefix web run gen:api`.
- [x] 1.9 `web`: satıcı sipariş detayında "Kargoya hazırla" (koli boyutu seçimi, onay), gönderi kartı (firma, büyük ve kopyalanabilir gönderi kodu, durum), etiket önizleme ve "Yazdır"; `/api/seller/orders/[id]/label` Route Handler'ı; dört durum; metinler `tr.json` + `en.json`; 375 px'de dokunma hedefleri 44 px.
- [x] 1.10 `web` testleri: kargoya hazırla başarı / sunucu hatası eşlemesi / telefon yok hatası; gönderi kartı; Route Handler başlık aktarımı ve yetkisiz durum.

## Faz 2 · Durum takibi: poller → geçişler → e-posta → alıcı ekranı

Bu faz bittiğinde dev'de açılan gönderi birkaç dakikada "Kargoya verildi", "Yolda", "Teslim edildi" olur; alıcı ve satıcı görür.

- [x] 2.1 `@EnableScheduling`; `ShipmentStatusPoller` (D5): parti seçimi `last_polled_at` sırasıyla, gönderi başına ayrı transaction ve kilit, geçişlerin sırayla uygulanması, geri giden bildirimin yok sayılması, `CANCELLED` bildiriminin gönderiye yansıması, hata izolasyonu. Geçişler `order` içinde tek giriş noktasından (`SellerOrderService` veya mevcut servisin genişlemesi — K9: önce mevcut `OrderService` incelenir) geçmişe `actor` boş yazılır.
- [x] 2.2 `OrderNotifier`'a "kargoya verildi" ve "teslim edilemedi" e-postaları (TR + EN şablon, hesap dili, transaction sonrası).
- [x] 2.3 `BuyerSellerOrderView`'e `shipment` (etiketsiz); sipariş geçmişi görünümü boş `actor`'ı "sistem" olarak okur.
- [x] 2.4 `api` testleri (`critical`): `HANDED_OVER` → `SHIPPED` + e-posta; `DELIVERED` → `DELIVERED` + `delivered_at`; `CREATED` → `DELIVERED` atlaması iki geçiş; `DELIVERY_FAILED` → e-postalar; geri giden bildirim yok sayılır; bir gönderinin hatası diğerlerini durdurmaz; `SHIPPED` sipariş iptal edilemez (409); `pollingEnabled=false` testlerde zamanlayıcıyı kapatır. Sabit `Clock`, test entegratörü.
- [x] 2.5 Sözleşme yeniden üretilir; `web` tipleri.
- [x] 2.6 `web`: alıcı sipariş detayında gönderi bilgisi (firma, takip numarası, durum, zamanlar); alıcı ve satıcı ekranlarında yeni durum etiketleri (`SHIPPED`, `DELIVERED`, `DELIVERY_FAILED`, `REFUNDED`, `CLOSED`); satıcı liste süzgeci; kargoya verilmiş siparişte iptal düğmesi görünmez (otorite yine `api`). Testler.

## Faz 3 · İptal ve teslim edilemeyen sipariş: iptal değişikliği → admin kararı

Bu faz bittiğinde gönderisi açılmış sipariş iptal edilebilir ve admin teslim edilemeyen siparişi çözer.

- [x] 3.1 `ShipmentService.cancelActive`; `OrderCancellationService.cancel` D6 sırasıyla günceller (gönderi iptali → stok → geçiş → iade). Faz 1'deki "gönderisi açık siparişin iptali 409" geçici kuralı kaldırılır.
- [x] 3.2 `RefundReason.UNDELIVERED`; `AdminSellerOrderController` ve servis (D7): liste (sayfalı, ortak şekil, `status` beyaz listesi), detay (alıcı adı ve telefonu, açık adres yok), karar (`REFUND` / `CLOSE`, gerekçe kuralı, kilit, iade, geçmiş, e-postalar). DTO'lar `@Schema` ile; `SecurityConfig` admin kuralı; mesajlar.
- [x] 3.3 `api` testleri (`critical`): gönderisi açık sipariş iptali gönderiyi iptal eder ve tam iade yapar; entegratör iptal reddi → 502, hiçbir şey değişmez; iade başarısız → geri alma (gönderi entegratörde iptal, sonraki sorguda `CANCELLED`); admin `REFUND` → iade tutarı ürün + kargo, stok değişmez, `REFUNDED`; `CLOSE` gerekçesiz 400, gerekçeli `CLOSED` ve iade yok; uygun olmayan durum 409; admin olmayan 403; iade başarısız 502.
- [x] 3.4 Sözleşme yeniden üretilir; `web` tipleri.
- [x] 3.5 `web`: `/admin/orders` liste ve detay, karar diyaloğu (yıkıcı eylem onaylı, §12.5), admin gezinmesine bağlantı, `/api/admin/seller-orders/*` Route Handler'ları (CSRF), dört durum, metinler. Testler.

## Faz 4 · Doğrulama ve doküman

- [x] 4.1 §7 komutlarının tamamı: `./mvnw -B verify`, `-Dgroups=critical`, `npm --prefix web ci / lint / typecheck / test / build`; `./docs/.tools/serve.sh --build`. Çıktılar raporda olduğu gibi.
- [x] 4.2 `docker compose up` ile uçtan uca: dev alıcısı sipariş verir → dev satıcısı gönderi açar, etiketi görür → birkaç dakikada "Teslim edildi"; mailpit'te "kargoya verildi" e-postası. Satıcı detayı ve alıcı detayı 375 / 1440 px, iki temada başsız Chrome ile görüntülenir.
- [x] 4.3 ADR-071…078 (D1–D8) yazılır, `docs/adr/index.md` güncellenir.
- [x] 4.4 `docs/reference/` API ve ER sayfaları; `00-current-state.md` (özet, L1 güncellenir; yeni sınırlamalar: poller tek örnek, `DELIVERY_FAILED` dev'de denenemez, etiketler silinmiyor); `openspec/KALANLAR.md` A2 notu ve A5'e etiket saklama süresi; `docs/features/kargo-ve-teslimat.md` ve `sepet-ve-siparis.md` *Tarihçe*; gerekiyorsa `docs/how-to/` (dev'de `fake` sürelerini değiştirme).
- [x] 4.5 Spec sync (`openspec/specs/shipments/spec.md` yeni, `orders` delta birleşir; `orders` Purpose cümlesindeki "kargo durumları kapsam dışı" ifadesi güncellenir).
- [ ] 4.6 §14 raporu; archive kullanıcı onayıyla.

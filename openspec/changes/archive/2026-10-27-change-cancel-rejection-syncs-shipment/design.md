# Design: change-cancel-rejection-syncs-shipment

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-105, D2 → ADR-106.
> Çerçeve:
> - [ADR-075](../../../../docs/adr/075-shipment-status-by-scheduled-polling.md): gönderi durumu zamanlanmış sorguyla okunur; her gönderi kendi transaction'ında kilitlenip güncellenir.
> - [ADR-076](../../../../docs/adr/076-cancel-cancels-shipment-before-refund.md): iptal önce gönderiyi iptal eder, sonra iade; entegratör reddederse hiçbir şey değişmez. Bu karar aynen kalır; ADR-076'ya "Olumsuz" maddesinin giderildiğini söyleyen not düşülür.

## Kararlar

### D1 · `api`: iptal reddedilince gönderinin durumu hemen okunur; kargoya verilmişse 409

- **Transaction sınırı:** `OrderCancellationService.cancelByBuyer` / `cancelBySeller` iptali `TransactionTemplate` içinde yapar. Entegratör iptali reddederse (`ShippingIntegratorException`) transaction geri alınır: sipariş, gönderi, stok değişmez, iade yapılmaz (ADR-076 aynen).
- **Red sonrası:** transaction dışında, kilitler bırakıldıktan sonra `ShipmentStatusPoller.refreshActive(sellerOrderId)` çağrılır:
  - satıcı siparişinin etkin gönderisini kendi transaction'ında kilitler ve durum sorgusunun tek gönderi adımını (`pollOne`) uygular: aynı geçişler, aynı e-postalar, aynı geçmiş satırı ("sistem");
  - dönüş: gönderi artık kargoya verilmiş mi (`HANDED_OVER` ve sonrası).
  - Durum okunamazsa (entegratöre ulaşılamaz) `WARN` log'u yazılır ve "verilmemiş" döner; gönderi bir sonraki turda zaten sorgulanır.
- **Yanıt:**
  - kargoya verilmişse `409 ORDER_ALREADY_SHIPPED`, mesaj `error.orderAlreadyShipped`;
  - değilse `502 SHIPPING_INTEGRATOR_ERROR` (bugünkü gibi).
- **Kapsam:** hem alıcı hem satıcı iptali. Satıcı da paketi verip hemen iptal etmeye çalışabilir; aynı gerçek, aynı yanıt.

**Gerekçe:**
- Reddin nedeni, gönderinin entegratördeki durumundadır. Durumu okumak nedeni portu değiştirmeden öğrenir ve sistemi aynı anda gerçeğe getirir: sayfa yenilendiğinde iptal düğmesi kalkar.
- Durum güncellemesi sorgu koduyla yapılır (K9): geçiş kuralları, e-posta ve çift geçiş koruması tek yerde kalır.
- Okuma, iptal transaction'ı geri alındıktan **sonra** yapılır. İçeride yapılsaydı ya geri alınırdı ya da aynı satırları ikinci bir transaction'da kilitlemeye çalışıp kilitlenirdi.

**Reddedilen alternatifler:**
- (a) **Portta red nedenini ayırmak (`ShipmentAlreadyHandedOverException`):** gerçek entegratörün hata biçimi bilinmiyor (A2); bilinse de sistemdeki gönderi durumu eski kalır ve sayfa yine iptal düğmesi gösterir.
- (b) **Yalnızca mesajı değiştirmek ("kargoya verilmiş olabilir"):** belirsiz; ulaşılamama durumunda yanlış bilgi verir ve durum eski kalır.
- (c) **Durum sorgusu aralığını kısaltmak:** pencereyi daraltır, kapatmaz; entegratöre yük.
- (d) **Red sonrası durumu iptal transaction'ı içinde okuyup kaydetmek (`noRollbackFor`):** iptalin "ya hepsi ya hiçbiri" sınırını bulanıklaştırır; kısmi yazımı ADR-076'nın koruduğu transaction'a sokar.

### D2 · `web`: mesaj pencerede kalır, pencere kapanınca sayfa yenilenir

- `CancelSellerOrder` hata `ORDER_ALREADY_SHIPPED` ise hatayı pencereye bırakır (sunucunun mesajı formun üstünde görünür) ve sayfanın eski olduğunu not eder.
- Pencere kapanınca (`ReasonDialog`'a eklenen isteğe bağlı `onClose`) sayfa `router.refresh()` ile yenilenir; satıcı siparişi "Kargoda" ve takip numarasıyla görünür, iptal düğmesi kalkar.
- Diğer hatalarda davranış aynı.

**Gerekçe:**
- Hatada hemen yenilemek, düğmeyi ve onunla pencereyi kaldırır; kullanıcı mesajı okuyamadan kaybeder.
- Yeni metin gerekmez: mesaj `api`'den, isteğin dilinde gelir (ADR-031).

**Reddedilen alternatifler:**
- (a) **Hatada hemen yenilemek:** mesaj kaybolur.
- (b) **Sayfa düzeyinde ayrı bir bildirim alanı:** yeni durum yönetimi; pencere zaten mesaj gösteriyor.

## Yetkilendirme ve kişisel veri

- Yetkilendirme değişmez: durum okuması yalnızca, sahiplik denetiminden geçmiş bir iptal isteğinin reddinden sonra ve aynı satıcı siparişi için yapılır.
- 409 yanıtı, başkasının siparişi için hiçbir zaman dönmez: sahiplik denetimi (404) ilk adımdır.
- Kişisel veri log'a yazılmaz; yalnızca kimlikler.

## Test etkisi

- `critical` (Testcontainers, sahte entegratör, `MutableClock`):
  - alıcı, gönderisi şubeye verilmiş ama sistemde `CREATED` görünen siparişi iptal eder → 409 `ORDER_ALREADY_SHIPPED`; satıcı siparişi `SHIPPED`, gönderi `HANDED_OVER`, alıcıya "kargoya verildi" e-postası; iade yok, stok değişmez;
  - aynı durum satıcı iptalinde;
  - entegratör reddeder ve durum hâlâ `CREATED` → 502, hiçbir şey değişmez (mevcut test korunur);
  - durum okuması da başarısız → 502, hiçbir şey değişmez.
- Mevcut testler: "Entegratör gönderi iptalini reddeder" testi sahte entegratörün nasıl reddettiğine göre gözden geçirilir.
- `web`: `CancelSellerOrder` testi: 409'da mesaj görünür, yenileme pencere kapanınca olur; başarıda bugünkü gibi hemen.

## Risks / Trade-offs

- Red durumunda yanıt bir entegratör çağrısı kadar uzar.
- Durum okuması "sistem" geçişi yazar; geçmişte iptal denemesi görünmez (bugün de görünmüyor).

## Migration Plan

Şema ve veri değişmez. Geri alma: commit'in geri alınması.

## Open Questions

Yok.

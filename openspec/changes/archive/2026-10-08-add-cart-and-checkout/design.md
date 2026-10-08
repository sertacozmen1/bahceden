# Design: add-cart-and-checkout

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altında bir ADR olur (`AGENTS.md` K1 / §5.2). Eşleşme:
>
> | Karar | ADR |
> |---|---|
> | D1 | ADR-059 |
> | D2 | ADR-060 |
> | D3 | ADR-061 |
> | D4 | ADR-062 |
> | D5 | ADR-063 |
> | D6 | ADR-064 |
> | D7 | ADR-065 |
> | D8 | ADR-066 |
> | D9 | ADR-067 |
> | D10 | ADR-068 |
>
> Aşağıdaki kararlar bu çerçeve kararlarının uygulama ayrıntılarıdır ve hiçbirine aykırı değildir:
>
> | ADR | Çerçeve |
> |---|---|
> | [ADR-054](../../../../docs/adr/054-split-payment-via-provider-port-provider-pending.md) | Bölünmüş ödeme |
> | [ADR-055](../../../../docs/adr/055-commission-percent-plus-fixed-from-seller.md) | Komisyon |
> | [ADR-056](../../../../docs/adr/056-cancel-before-shipment-and-defect-refund.md) | İptal ve iade |
> | [ADR-058](../../../../docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md) | Kargo ücreti ve süre |
> | [ADR-006](../../../../docs/adr/006-money-as-minor-unit.md) | Para kuruş cinsinden |
> | [ADR-007](../../../../docs/adr/007-query-level-ownership-scoping.md) | Sorgu seviyesinde sahiplik |
> | [ADR-044](../../../../docs/adr/044-seller-role-follows-status-with-query-gate.md) | Onaylı satıcı kapısı |

## Kararlar

### D1 · `payment` ve `order` paketleri açılır; satın alınabilirlik tek bir koşuldur ve vitrine alan olarak çıkar

`AGENTS.md` §11.2 domain paketlerini zaten sayıyor. Paketler şöyle bölünür:

- **`com.bahceden.payment`:**
  - satıcının ödeme hesabı;
  - ödeme sağlayıcısı arayüzü ve `fake` uygulaması;
  - ödeme ve iade kayıtları.
- **`com.bahceden.order`:**
  - sepet;
  - adresler;
  - sipariş, satıcı siparişi ve satırlar;
  - fiyatlama;
  - iptal.
- **Paketler arası yön:**
  - `order`, `payment`'ı ve `catalog`'u çağırır;
  - `payment`, `order`'ı bilmez (sağlayıcıya giden istek kendi tiplerindedir);
  - `catalog`, `order`'ı bilmez.

Bir ürün şu dört koşulun hepsi sağlandığında **satın alınabilir** sayılır:

1. `PUBLISHED` durumundadır;
2. satıcısı `APPROVED` durumundadır;
3. satıcının ödeme hesabı `ACTIVE` durumundadır;
4. `stock_quantity > 0`.

Koşul tek bir yerde, vitrin sorgusunun yanında tanımlanır. Vitrin listesi ve ürün detayı bu koşulun sonucunu `purchasable` alanıyla taşır. Sepet ve sipariş de aynı koşulu kullanır.

Satın alınamayan ürün vitrinde görünmeye devam eder. Ekranda "Sepete ekle" yerine nedeni yazar:

- stok tükendiyse "Tükendi";
- diğer durumlarda "Satıcı henüz satışa hazır değil".

**Gerekçe:**
- Hesapsız seed satıcıları ve ödeme bilgisini henüz girmemiş satıcılar vitrinden kaybolmaz. Vitrin spec'indeki görünürlük kuralı değişmez.
- Koşul tek yerde olduğu için vitrin, sepet ve sipariş aynı kararı verir.

**Reddedilen alternatifler:**
- (a) Ödeme hesabı olmayan satıcının ürününü vitrinden gizlemek: vitrin bugünkü haliyle boşalır ve görünürlük kuralı ikinci kez değişir.
- (b) Kontrolü yalnızca siparişte yapmak: alıcı ödeme adımında hata alır. Ekranın bunu baştan bilmesi gerekir.
- (c) Tek bir `commerce` paketi: §11.2'deki domain ayrımına aykırı; ödeme sağlayıcısı kodu siparişle iç içe geçer.

### D2 · Satıcı ödeme hesabı ayrı bir tabloda; kimlik numarası ve IBAN sunucuda doğrulanır, yanıtta maskelenir; alt üye kaydı eşzamanlıdır

**Tablo `seller_payout_accounts`** (satıcı başına bir satır, `seller_id` benzersiz). Kolonlar:

| Kolon | İçerik |
|---|---|
| `legal_type` | `INDIVIDUAL` (bireysel), `SOLE_PROPRIETOR` (şahıs şirketi) veya `COMPANY` (şirket) |
| `legal_name` | yasal ad |
| `tax_id` | kimlik numarası |
| `tax_office` | vergi dairesi |
| `iban` | IBAN |
| `status` | `ACTIVE` veya `PROVIDER_REJECTED` |
| `provider_sub_merchant_key` | sağlayıcının alt üye anahtarı |
| zaman damgaları | |

**Doğrulama (`payment` paketinde, saf Java, `critical` testli):**
- **Kimlik numarası türe göre değişir:**
  - `INDIVIDUAL` ve `SOLE_PROPRIETOR` için TCKN: 11 hane, ilk hane 0 değil, iki sağlama hanesi;
  - `COMPANY` için VKN: 10 hane.
  - VKN'nin sağlama algoritması kamuya açık ama resmi değil; bu yüzden yalnızca biçim denetlenir.
- **Vergi dairesi:** `SOLE_PROPRIETOR` ve `COMPANY` için zorunlu.
- **IBAN:** `TR` + 24 hane, mod-97 sağlaması. Boşluklar kabul edilir ve kaydedilmeden önce atılır.

**Kayıt ve güncelleme:**
- `PUT /api/v1/sellers/me/payout-account` kaydı oluşturur veya günceller.
- Ardından aynı istekte sağlayıcıda alt üye kaydını açar veya günceller.
- Sağlayıcı reddederse:
  - kayıt `PROVIDER_REJECTED` olur;
  - istek 422 `PAYOUT_ACCOUNT_REJECTED` alır;
  - satıcının ürünleri satın alınamaz kalır.
- Satıcı ödeme hesabı ucu da onaylı satıcı kapısından geçer ([ADR-044](../../../../docs/adr/044-seller-role-follows-status-with-query-gate.md)).

**Maskeleme:**
- Yanıt IBAN'ı `TR** **** **** **** **** **12 34` biçiminde, kimlik numarasını da son dört haneyle döner.
- Tam değer hiçbir uçtan geri okunmaz ve log'a yazılmaz.
- Satıcı değiştirmek için tam değeri yeniden girer.

**Saklama:** değerler veritabanında düz metindir. Uygulama düzeyinde şifreleme bu change'te yoktur; disk ve yedek şifrelemesi KALANLAR B5 ile ele alınır.

**Gerekçe:**
- Alt üye kaydı satılabilirliğin ön koşulu (ADR-054). Kaydı eşzamanlı yapmak "kayıt bekliyor" diye ayrı bir durum ve arka plan işi gerektirmez.
- Maskeli yanıt, ekranın kaydı gösterebilmesi için yeterli. Tam değeri geri okumamak, sızıntı yüzeyini kayıt anına indirir.

**Reddedilen alternatifler:**
- (a) Ödeme bilgisini satıcı başvurusuna eklemek: [ADR-037](../../../../docs/adr/037-seller-application-with-document-and-manual-approval.md) §4 başvuruda bu verileri almıyor. Onaylanmamış satıcının IBAN'ını tutmak gereksiz veri toplamaktır.
- (b) Uygulama düzeyinde AES şifreleme: ayrı bir anahtar ve anahtar rotasyonu getirir. Anahtar veritabanıyla aynı sunucuda durduğu için tek sunucuda kazanım sınırlı; B5 kararıyla birlikte değerlendirilir.
- (c) Asenkron alt üye kaydı: kuyruk veya zamanlanmış iş gerekir (§3). `fake` ve gerçek sağlayıcıların kayıt API'si eşzamanlı.

### D3 · İlanda kargoya verme süresi; son tarih iş günüyle hesaplanır, yalnızca hafta sonu dışlanır

**Şema ve sözleşme:**
- `products.dispatch_days` kolonu: `integer not null default 2`, değer 1–3. Mevcut ilanlar 2 alır.
- `ListingRequest.dispatchDays` isteğe bağlıdır; gönderilmezse yeni ilanda 2 olur, güncellemede mevcut değer korunur. Böylece sözleşme kırılmaz.
- Değer satıcının ilan ekranında, ürün detayında ve sepette görünür.

**Satıcı siparişindeki süre:**
- Satıcı siparişinin süresi, içindeki ürünlerin en uzun süresidir. Bu süre siparişe yazılır.
- Ödeme anından itibaren son tarih (`dispatch_due_at`) hesaplanır: Türkiye saatiyle (`Europe/Istanbul`) iş günü sayılır, cumartesi ve pazar atlanır, son günün 23:59:59'u alınır, UTC olarak saklanır.
- Resmi tatiller hesaba katılmaz.

**Gerekçe:**
- [ADR-058](../../../../docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md) olumsuz sonucu: mevcut ilanlara bir varsayılan gerekiyor.
- İsteğe bağlı istek alanı kırıcı değişikliği önler.
- Tatil takvimi yıllık bakım isteyen bir veri. Hafta sonu kuralı MVP için yeterince dürüst.

**Reddedilen alternatifler:**
- (a) Alanı istekte zorunlu yapmak: sözleşmede `required` değişikliği (§9) olur ve tek istemci de aynı PR'da güncellense bile gereksiz.
- (b) Resmi tatil listesini yapılandırmada tutmak: her yıl elle güncellenir, unutulursa son tarih sessizce yanlış olur. İhtiyaç görülürse ayrı bir karar konusu.
- (c) Takvim günü: cumartesi verilen sipariş pazartesi kargolanabilir; satıcı haksız yere "süre aştı" durumuna düşer.

### D4 · Sepet sunucuda, hesaba bağlı tek kayıt; fiyat ve stok her okumada üründen gelir

**Tablolar:**
- `carts`: `account_id` benzersiz.
- `cart_items`: `cart_id`, `product_id`, `quantity`, zaman damgaları; `(cart_id, product_id)` benzersiz.

**Sepette fiyat tutulmaz.** `GET /cart` her satır için ürünün güncel fiyatını, stoğunu ve satın alınabilirliğini (D1) okur. Ardından satırları satıcıya göre gruplar ve her grup için şunları hesaplar (D7): ürün ara toplamı, kargo ücreti, ücretsiz kargoya kalan tutar.

**`PUT /cart/items/{productId}` `{quantity}`:**
- Satırı ekler veya adetini değiştirir.
- Adet 1 ile 99 arasında olmalı ve ürünün stoğunu aşmamalıdır.
- Satın alınamayan ürün eklenemez (422 `PRODUCT_NOT_PURCHASABLE`).
- Satıcı kendi ürününü ekleyemez (422 `CANNOT_BUY_OWN_PRODUCT`).

**Sepet sınırı:** en çok 50 satır.

**Ürün kimliği:** sepet uçları ürünü kimliğiyle alır. Bu yüzden herkese açık ürün detayına (`ProductDetail`) `id` alanı eklenir. Kimlik bir güvenlik sırrı değildir; yazma uçları zaten oturum ve satın alınabilirlik denetimi ister.

**Satın alınamaz hale gelen ürün:** sepette kalır ama `available = false` işaretlenir ve toplama katılmaz. Alıcı satırı siler veya ürün yeniden satılabilir olur.

**Sorgu sayısı:** sepet okuması ürünleri, satıcıları ve ödeme hesaplarını tek sorguda getirir; bu bir sorgu sayısı testiyle sabitlenir.

**Gerekçe:**
- Fiyatı sepette tutmamak, alıcının her zaman güncel fiyatı görmesini sağlar.
- Fiyat siparişte sabitlenir (D6).
- Hesaba bağlı sepet cihazlar arasında korunur; ürün sahibi misafir sepeti istemedi.

**Reddedilen alternatifler:**
- (a) Sepete eklenirken fiyatı sabitlemek: satıcı fiyatı düşürdüğünde alıcı eski fiyatı öder, yükselttiğinde satıcı zarar eder; iki fiyat kaynağı oluşur.
- (b) Sepeti `web` çerezinde tutmak: fiyat ve stok yine sunucudan okunmak zorunda; çerez boyutu ve iki kaynak birleştirme kuralı gelir.

### D5 · Teslimat illeri yapılandırmadan; il kataloğu sabit; hesabın en çok 10 adresi var

**İl kataloğu:**
- `common` altında Türkiye'nin 81 ili, plaka koduyla ve tek dilde adıyla sabit bir katalogda durur (`TurkishProvinces`). İl adı özel isimdir ve çevrilmez (§12.4).
- Teslimat yapılan iller `bahceden.commerce.delivery-provinces` listesindedir: `34, 35, 06, 45`. Açılışta her kodun katalogda olduğu doğrulanır.
- `GET /api/v1/delivery/provinces` herkese açıktır ve bu listeyi döner.

**Tablo `addresses`:**

| Kolon | Kural |
|---|---|
| `account_id` | adresin sahibi |
| `title` | "Ev" gibi bir etiket, 40 karaktere kadar |
| `recipient_name` | 120 karaktere kadar |
| `phone` | satıcı başvurusundaki telefon normalleştirmesiyle aynı kural, yeniden kullanılır (K9) |
| `province_code` | teslimat illerinden biri |
| `district` | 80 karaktere kadar |
| `address_line` | 250 karaktere kadar |
| `postal_code` | 5 hane, isteğe bağlı |

**Uçlar:** `GET/POST /api/v1/addresses`, `PUT/DELETE /api/v1/addresses/{id}`.
- Sahiplik sorgu seviyesinde denetlenir; başkasının adresi 404 döner.
- Bir hesabın en çok 10 adresi olabilir.
- Adres silindiğinde geçmiş siparişler etkilenmez, çünkü sipariş adresin bir kopyasını taşır (D6).

**Teslimat dışı kalan il:** il, adres kaydedildikten sonra teslimat listesinden çıkarılırsa adres kayıtlı kalır ama `deliverable = false` döner. Bu adresle sipariş verilemez (422 `ADDRESS_NOT_DELIVERABLE`).

**Gerekçe:**
- İl listesi ticari bir karar ve büyüyecek. Yapılandırmada durması, il eklemeyi kod değişikliği olmaktan çıkarır.
- Sabit katalog, yapılandırmadaki yazım hatasını açılışta yakalar.

**Reddedilen alternatifler:**
- (a) İl ve ilçe için veritabanı tablosu ve ilçe kataloğu: 900'den fazla ilçe bakım yükü getirir. Kargo entegratörü ilçe adını serbest metin olarak kabul ediyor; doğrulaması gönderi change'inde yapılır.
- (b) Adresi yalnızca siparişte almak (adres defteri olmadan): tekrar alışverişte alıcı adresi her seferinde yeniden yazar.
- (c) İl kodunu enum yapmak: 81 değer kodda durur ve teslimat listesi yine yapılandırmada olur. Katalog yeterli.

### D6 · Sipariş üç seviyede: sipariş, satıcı siparişi, satır; her tutar ve adres sipariş anında yazılır; `Idempotency-Key` desteklenir

**Tablolar:**
- **`orders`:**
  - iş anahtarı `order_number` (benzersiz, `BHC-` + 10 karakter Crockford base32, rastgele);
  - `buyer_account_id`;
  - `status`;
  - teslimat adresi kopyası (`ship_*` kolonları);
  - `items_total`, `shipping_total`, `grand_total`, `currency`;
  - `idempotency_key` (`buyer_account_id` ile birlikte benzersiz);
  - `payment_expires_at`;
  - zaman damgaları.
- **`seller_orders`:**
  - `order_id`, `seller_id`, `status`;
  - `items_total`, `shipping_fee`;
  - `commission_rate_bp`, `commission_fixed`, `commission_amount`, `seller_payout`;
  - `dispatch_days`, `dispatch_due_at`;
  - `cancelled_by`, `cancel_reason`;
  - zaman damgaları.
- **`order_items`:**
  - `seller_order_id`, `product_id`;
  - kopya alanlar: `product_name`, `product_slug`, `unit`;
  - `unit_price`, `quantity`, `line_total`.
- **`order_status_changes`:** satıcı siparişi seviyesinde, yalnızca eklenir. Satıcı kaydı ve ilanla aynı desen.

**Durumlar:**

| Seviye | Değerler |
|---|---|
| `orders.status` | `PENDING_PAYMENT`, `PAID`, `PAYMENT_FAILED`, `PAYMENT_EXPIRED`, `CANCELLED_OUT_OF_STOCK` |
| `seller_orders.status` | `PENDING_PAYMENT`, `NEW` (ödendi, satıcı hazırlıyor), `CANCELLED`, ödeme başarısızsa `VOID` |

Kargo durumları (`SHIPPED`, `DELIVERED`) gönderi change'inde eklenir.

Satıcı siparişi geçişleri `SellerOrderTransition` enum'unda tek yerde tutulur. Desen `ProductTransition` ile aynıdır.

**Sipariş oluşturma (`POST /api/v1/orders`, `{addressId}`, `Idempotency-Key` başlığı zorunlu):**
1. Sepetteki satırlar okunur. Satın alınamayan ya da adedi güncel stoğu aşan satır varsa istek reddedilir (422 `CART_HAS_UNAVAILABLE_ITEMS`; sepet bu satırı zaten "satın alınamaz" gösterir). Sepet boşsa 422 `CART_EMPTY`.
2. Stok bu adımda düşmez.
3. Satırlar satıcıya göre bölünür.
4. Tutarlar D7 ile hesaplanır.
5. Adres kopyalanır.
6. Sipariş `PENDING_PAYMENT` olarak yazılır.
7. Ödeme başlatılır (D8).

Aynı anahtarla gelen ikinci istek yeni sipariş açmaz; aynı siparişi döner.

**Sahiplik:**
- Alıcının sipariş sorguları `buyer_account_id` ile kurulur.
- Satıcınınkiler `requireApprovedSeller` kapısından sonra `seller_id` ile kurulur.
- İki tarafta da başkasının kaydı 404 döner.
- Satıcı yanıtında alıcı adresinden yalnızca il ve ilçe bulunur.

**Gerekçe:**
- Her satıcı ayrı paket gönderir, ayrı komisyon öder ve ayrı iptal edilebilir. Satıcı siparişi bu kararların birimi ([ADR-055](../../../../docs/adr/055-commission-percent-plus-fixed-from-seller.md)).
- Sipariş anında yazılan tutarlar, sonradan değişen fiyat veya yapılandırmadan etkilenmez.
- `Idempotency-Key`, çift tıklamanın iki sipariş açmasını önler (§11.5).

**Reddedilen alternatifler:**
- (a) Satıcı başına ayrı sipariş ve ayrı ödeme: alıcı sepetteki her satıcı için ayrı kart işlemi yapar. Bölünmüş ödeme tek işlemde bunu çözüyor.
- (b) Tek seviyeli sipariş (satıcı ayrımı yalnızca satırlarda): iptal, komisyon ve kargo için satıcı ayrımı her sorguda yeniden hesaplanır.
- (c) Sıralı sipariş numarası: toplam sipariş hacmini dışarı sızdırır ve tahmin edilebilir.

### D7 · Fiyatlama tek bir sınıfta: kargo ücreti ve komisyon kuruş tamsayısıyla, `HALF_UP`; değerler yapılandırmada

`order` paketinde `OrderPricing` saf bir hesap sınıfıdır. Durum tutmaz ve saat kullanmaz. Girdi bir satıcı grubunun ürün tutarı ve yapılandırmadır.

**Kargo ücreti** ([ADR-058](../../../../docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md)): `ürün tutarı >= free_shipping_threshold` ise 0, değilse `shipping_fee`.

**Komisyon** ([ADR-055](../../../../docs/adr/055-commission-percent-plus-fixed-from-seller.md)):
- `komisyon = min(ürün tutarı, yuvarla(ürün tutarı × rate_bp / 10000) + fixed)`.
- Oran baz puan (bp) cinsindendir: %5 = 500.
- Yuvarlama tamsayı aritmetiğiyle yapılır: `(tutar × bp + 5000) / 10000`. Tutar negatif olmadığı için bu `HALF_UP` ile aynıdır.
- `long` taşması için üst sınır: satır fiyatı ≤ 10.000.000 kuruş × 99 adet × 50 satır, `long` içinde kalır.

**Hakediş:** `satıcı hakedişi = ürün tutarı − komisyon`.

**Yapılandırma** `bahceden.commerce` altındadır, açılışta `@Validated` ile denetlenir:

| Anahtar | Değer |
|---|---|
| `commission-rate-bp` | 500 |
| `commission-fixed-minor` | 300 |
| `shipping-fee-minor` | 9900 |
| `free-shipping-threshold-minor` | 100000 |
| `payment-timeout` | `PT30M` |
| `delivery-provinces` | il kodları listesi |

Sepet (D4) ve sipariş (D6) aynı sınıfı kullanır. Ekrandaki toplam ile alınan para bu sayede ayrışamaz.

**Gerekçe:**
- Para kuralı tek yerde ve `critical` testle sabit (§11.3).
- Tamsayı aritmetiği kayan nokta hatasını dışarıda bırakır.

**Reddedilen alternatifler:**
- (a) `BigDecimal` ile yüzde hesabı: doğru sonuç verir ama tek bir çarpma için gereksiz; yuvarlama kipi yine açıkça seçilmek zorunda.
- (b) Oranı veritabanında tutmak: değiştirme yüzeyi (admin ekranı) bu change'te yok. Yapılandırma ve sipariş anı kopyası yeterli.

### D8 · Ödeme: başlat, yönlendir, tamamla; `fake` sağlayıcı onaylar; stok tamamlama anında kilitle düşer, yetmezse tüm ödeme iade edilir

**`PaymentProvider` arayüzü** (`payment` paketi), [ADR-054](../../../../docs/adr/054-split-payment-via-provider-port-provider-pending.md) gereği yalnızca bu modelin ihtiyacı kadardır:

| Metot | İş |
|---|---|
| `registerSubMerchant(details)` | alt üye anahtarı döner |
| `initiate(paymentRequest)` | sağlayıcı ödeme referansını ve alıcının gideceği adresi döner |
| `verify(reference)` | ödeme sonucunu döner: başarılı, başarısız veya bekliyor |
| `refund(reference, sellerShare, amount)` | iade yapar |

`paymentRequest` her satıcı payı için alt üye anahtarını, satıcıya gidecek tutarı ve pazaryeri payını (komisyon + kargo ücreti) taşır.

**Akış:**
1. `POST /orders` siparişi yazar ve `initiate` çağırır. `payments` satırı `INITIATED` durumunda açılır. Yanıt `{orderNumber, paymentRedirectUrl}` döner.
2. `web` alıcıyı bu adrese gönderir. `fake` sağlayıcının adresi doğrudan `web`'in `/checkout/return?order=…` sayfasıdır, yani alıcı bir ara ekran görmez. Gerçek sağlayıcıda bu adres 3D Secure sayfası olur.
3. Dönüş sayfası `POST /api/v1/orders/{orderNumber}/payment` çağırır. `api`, sağlayıcıdan `verify` ile sonucu alır. İstemcinin söylediği sonuca güvenilmez (§11.1).
4. **Başarılıysa tek transaction'da:**
   1. siparişin ürünleri `SELECT … FOR UPDATE` ile, deadlock'u önlemek için `id` sırasıyla kilitlenir;
   2. her satırın stoğu yeterliyse stok düşer;
   3. sipariş `PAID`, satıcı siparişleri `NEW` olur;
   4. `dispatch_due_at` hesaplanır (D3);
   5. satın alınan ürünler sepetten silinir;
   6. e-postalar transaction sonrasında gönderilir.
5. **Stok yetmezse:** sağlayıcıdan tüm ödeme iade edilir, sipariş `CANCELLED_OUT_OF_STOCK` olur ve sepet olduğu gibi kalır. Bu davranış `critical` testle sabitlenir.
6. **Ödeme başarısızsa:** sipariş `PAYMENT_FAILED`, satıcı siparişleri `VOID` olur. Sepet olduğu gibi kalır.
7. **Süre dolduysa:** tamamlama çağrısı `payment_expires_at`'ten (30 dk) sonra gelirse sipariş `PAYMENT_EXPIRED` olur. Sağlayıcı ödemeyi yine de başarılı bildirirse ödemenin tamamı iade edilir (`refunds.reason = PAYMENT_EXPIRED`). Bu kontrol çağrı anında yapılır; zamanlanmış iş yoktur.
8. **Tekrar çağrı:** tamamlama idempotent'tir. Sonuçlanmış siparişe yapılan ikinci çağrı, mevcut sonucu döner.

**`FakePaymentProvider`:**
- `bahceden.payment.provider=fake` iken etkindir; şimdilik başka değer yoktur, açılışta doğrulanır.
- Her işlemi onaylar.
- Testler başarısızlık ve iade dallarını ayrı bir test uygulamasıyla sınar.
- Gerçek ağ çağrısı yapmaz.

**Gerekçe:**
- Gerçek sağlayıcılar (3D Secure) yönlendirme ve dönüş ister. `fake` aynı biçimde kurulursa gerçek sağlayıcı eklendiğinde akış değişmez.
- Stok kilidi tamamlama anında olduğu için aynı son ürünü iki alıcı ödese bile stok negatife düşmez. Ürün sahibi rezervasyon istemedi.

**Reddedilen alternatifler:**
- (a) `fake` için `web`'de "başarılı / başarısız öde" ara ekranı: üretim kod tabanında yalnızca geliştirme için var olan bir ekran olur (K6).
- (b) Eşzamanlı tek çağrıda ödeme (yönlendirmesiz): gerçek sağlayıcı geldiğinde akış ve ekranlar değişir.
- (c) İyimser kilit (`@Version`) ile stok: çakışmada tekrar deneme döngüsü gerekir. Kısa süreli satır kilidi bu ölçekte daha basit (§11.4 ikisine de izin veriyor).
- (d) Stok yetmeyen satıcıyı kısmen iade edip kalanını kabul etmek: alıcı eksik bir sepet için ödemiş olur; ürün sahibi tam iade dedi.

### D9 · Kargodan önce iptal: alıcı her zaman, satıcı gerekçeyle; satıcı siparişinin tamamı iade, stok geri eklenir

`NEW` durumundaki satıcı siparişi iki uçtan iptal edilir:

| Uç | Kim | Gerekçe |
|---|---|---|
| `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/cancel` | alıcı | isteğe bağlı |
| `POST /api/v1/sellers/me/orders/{id}/cancel` | satıcı | zorunlu |

[ADR-056](../../../../docs/adr/056-cancel-before-shipment-and-defect-refund.md) §1–2'ye göre alıcının iptali süreye bağlı değildir: kargoya verilmemiş her siparişi iptal edebilir. Süre aşımı ekranda yalnızca bilgi olarak gösterilir ("Satıcı kargoya verme süresini aştı").

**İptalde tek transaction'da:**
1. satıcı siparişi `CANCELLED` olur, `cancelled_by` ve gerekçe yazılır;
2. ürünlerin stoğu geri eklenir;
3. komisyon ve hakediş geçersiz olur;
4. sağlayıcıdan `refund` çağrılır: tutar satıcı siparişinin ürün tutarı + kargo ücreti;
5. `refunds` satırı yazılır.

İade başarısız olursa transaction geri alınır ve istek 502 `PAYMENT_PROVIDER_ERROR` döner. Para ile kayıt ayrışmaz.

E-posta karşı tarafa hesabının dilinde gider. Gönderilemezse iptal yine geçerlidir ([ADR-019](../../../../docs/adr/019-email-via-smtp-provider-agnostic.md)).

**Gerekçe:** iptal, satıcı siparişinin birimidir. Tam iade ADR-056'nın kuralıdır. Stok geri eklenmezse iptal edilen ürün satılamaz kalır.

**Reddedilen alternatifler:**
- (a) Satır bazında iptal: kısmi iade ve komisyonun yeniden hesaplanması ayıplı ürün akışının konusu; bu change'in kapsamında değil.
- (b) Önce iptal, sonra asenkron iade: para ile kayıt arasında tutarsızlık penceresi açılır ve kuyruk gerekir.

### D10 · `web`: sepet ve ödeme vitrinde, siparişler hesapta, satıcı ekranları satıcı panelinde; sepet sayısı başlıkta sunucudan

**Rotalar:**

| Grup | Rota | Ne |
|---|---|---|
| Vitrin `(shop)` | `/cart` | sepet |
| | `/checkout` | adres ve özet |
| | `/checkout/return` | ödeme dönüşü |
| Hesap `(account)` | `/account/orders` | siparişlerim |
| | `/account/orders/[number]` | sipariş detayı |
| | `/account/addresses` | adreslerim |
| Satıcı `(seller)` | `/seller/payout` | ödeme bilgileri |
| | `/seller/orders` | gelen siparişler |
| | `/seller/orders/[id]` | satıcı siparişi detayı |

**Davranış:**
- Bütün bu sayfalar oturum ister; oturumsuz istek girişe, ardından geri yönlendirilir.
- Ürün detayındaki "Sepete ekle" istemci yaprağıdır. Oturum yoksa giriş sayfasına `next` parametresiyle gider.
- Başlıktaki sepet sayısı, sunucu tarafında oturumla birlikte okunur. Sepet değişince sayfa `router.refresh()` ile yenilenir; istemci tarafında global sepet durumu tutulmaz.
- Para tutarları ekranda hesaplanmaz; sunucunun döndürdüğü toplamlar `money.ts` ile biçimlenir (§12.1).
- Son tarih `datetime.ts` ile biçimlenir.
- Mevcut bileşenler yeniden kullanılır: `FormField`, `Pagination`, `ListSkeleton`, `ReasonDialog` (satıcının iptal gerekçesi). `postToBff` ve `handleBffMutation` genişletilmeden kullanılır. Sipariş oluşturmada BFF `Idempotency-Key` başlığını iletir; anahtarı `web` formu üretir ve aynı denemede korur.
- **Ödeme bilgisi eksik satıcı:** "Ürünlerim" ve ilan detayında "Ürünlerinizin satın alınabilmesi için ödeme bilgilerinizi tamamlayın" uyarısı ve `/seller/payout` bağlantısı gösterilir.
- **Ödeme öncesi bilgi:** checkout özetinde "Taze ürünlerde teslimattan sonra cayma hakkı yoktur" bilgisi gösterilir (ADR-056 §3). "Ayıplı ürünü 48 saat içinde bildirebilirsiniz" cümlesi, ayıplı ürün akışıyla birlikte kendi change'inde eklenir. Akış yokken bu cümle işlevsiz bir vaat olur.

**Uygulamada eklenenler:** dar ekranda başlıktaki "Çıkış yap" yalnızca ikon olur (375 px'de yatay kaydırma olmaz). Admin listelerinde kopya duran durum süzgeci ortak `FilterTabs`'a, admin ve satıcı sekmeleri ortak `SectionTabs`'a çıkarıldı (K9). `ReasonDialog` alıcının iptali için isteğe bağlı gerekçe seçeneği kazandı. Sepet satırında adet seçici dar ekranda kendi satırına iner.

**Gerekçe:** rota grupları mevcut düzeni izler (§12.2). Sepet sayısını sunucuda okumak, ikinci bir istemci durumu ve token'ı tarayıcıya taşıma ihtiyacı doğurmaz ([ADR-023](../../../../docs/adr/023-web-bff-cookies-and-header-csrf.md)).

**Reddedilen alternatifler:**
- (a) İstemci tarafı sepet deposu (context veya kütüphane): iki doğruluk kaynağı oluşur ve state kütüphanesi K7 kararı ister.
- (b) Tek sayfalık checkout sihirbazı: adres defteri zaten ayrı bir ekran ihtiyacı. Adres, özet ve ödeme ayrımı alışılmış e-ticaret deseni (Jakob).

## Risks / Trade-offs

- **Aşırı satış ve iade deneyimi** (D8): ürün sahibi kabul etti. İade oranı loglanır (sipariş numarasıyla, kişisel veri olmadan).
- **Hassas verinin düz metin saklanması** (D2): yanıtta maskeleme ve log yasağı bir azaltma; asıl çözüm B5 (disk ve yedek şifrelemesi). `00-current-state.md`'ye bilinen sınırlama olarak yazılır.
- **`fake` sağlayıcı her şeyi onaylar:** dev ortamında başarısız ödeme elle denenemez. Testler başarısız ve iade dallarını ayrı bir test uygulamasıyla kapsar.
- **İş günü hesabı tatilleri bilmez** (D3): bayramlarda satıcı süre aşmış görünebilir. Süre aşımının yaptırımı yok (iptal zaten serbest), etkisi bilgi düzeyinde.
- **Change büyüklüğü:** dört faz, her biri tek başına çalışır (tasks.md).

## Migration Plan

1. **`011-cart-and-checkout.sql`** (`--rollback` satırlarıyla):
   - `products.dispatch_days integer not null default 2` ve `check (dispatch_days between 1 and 3)`.
   - Yeni tablolar: `seller_payout_accounts`, `carts`, `cart_items`, `addresses`, `orders`, `seller_orders`, `order_items`, `order_status_changes`, `payments`, `refunds`.
   - Yabancı anahtarlar.
   - Durum ve tutar `check` kısıtları: tutarlar ≥ 0; `quantity` 1–99.
   - İndeksler:
     - `orders(buyer_account_id, created_at desc)`;
     - `seller_orders(seller_id, created_at desc)`;
     - `orders(order_number)` benzersiz;
     - `orders(buyer_account_id, idempotency_key)` benzersiz;
     - `cart_items(cart_id, product_id)` benzersiz.
   - Ekleyicidir; mevcut veri değişmez.
2. **`012-dev-seed-cart-and-checkout.sql`** (`contextFilter:dev`, idempotent):
   - `uretici@ornek.test`'in satıcısına sahte, sağlaması geçerli bir ödeme hesabı ve `fake` alt üye anahtarı;
   - `alici@ornek.test`'e İzmir'de sahte bir adres (`Ornek Mahallesi`, `+90 555 000 00 00`).
   - `DevSeedIsolationTest` kapsamına eklenir.
3. Geri alma: rollback tabloları ters sırayla siler. Canlı veri yok.

## Open Questions

- **Sipariş için e-posta doğrulama kapısı:** [ADR-017](../../../../docs/adr/017-email-verification-non-blocking-and-2fa-switch.md) sipariş diliminin `email_verified` kapısını değerlendirmesini istiyor. Bu change kapı koymadı (e-posta doğrulaması girişi engellemiyor ve sipariş e-postaları doğrulanmamış adrese de gidiyor). Karar ürün sahibine soruldu; kapı gerekirse küçük bir change olur (`SELLER_EMAIL_NOT_VERIFIED` deseni, ADR-043).

- **Gerçek sağlayıcının dönüş biçimi:** dönüş adresi parametreleri ve webhook gerçek sağlayıcı seçilince netleşir (KALANLAR A1). Arayüzün `verify` metodu iki biçime de uyar.
- **Mesafeli satış sözleşmesi ve ön bilgilendirme formu:** metinler ve sipariş öncesi onay kutusu hukuk teyidi bekliyor (KALANLAR A5). Bu change'te yalnızca cayma bilgisi gösterilir.

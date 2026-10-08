# Tasks: add-cart-and-checkout

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema değişikliği ekleyicidir: yeni bir kolon (varsayılanlı) ve yeni tablolar; veri kaybı yok.
> Dış servise kişisel veri gönderimi ADR-054 ve ADR-057 ile onaylı; bu change'te sağlayıcı `fake` olduğu için dışarı veri gitmez.

## Faz 0 · Ürün belgesi

- [x] 0.1 `docs/product/sepet-ve-siparis.md` şablondan yazılır ve `docs/product/index.md` tablosuna eklenir. Belge şunları içerir:
  - kullanıcı problemi;
  - değer önerisi;
  - rakip analizi: büyük pazaryerlerinde sepet, satıcı başına kargo ve sipariş iptali;
  - kapsam ve kabul kriterleri (spec senaryolarıyla eşleşir);
  - başarı metrikleri: sepetten siparişe dönüşüm, ödeme başarısızlık oranı, stok yetersizliği iadesi oranı, satıcı iptal oranı.

## Faz 1 · Satıcı ödeme bilgileri, satın alınabilirlik ve kargoya verme süresi: `api` → sözleşme → `web`

Bu faz bittiğinde satıcı ödeme bilgilerini girer ve vitrin her ürünün satın alınabilir olup olmadığını gösterir. Sepet henüz yoktur.

- [x] 1.1 `011-cart-and-checkout.sql` design.md Migration Plan 1'e göre yazılır (`--rollback` dahil) ve master changelog'a `include` ile eklenir. Bütün tablolar bu tek changeset'tedir. Böylece sonraki fazlar şema değiştirmez.
- [x] 1.2 `payment` paketi kurulur:
  - `SellerPayoutAccount` entity'si, `PayoutLegalType` ve `PayoutAccountStatus` enum'ları;
  - `SellerPayoutAccountRepository`;
  - `TaxIdValidator` (TCKN sağlaması, VKN biçimi) ve `IbanValidator` (TR, mod-97);
  - `PaymentProvider` arayüzü (D8'deki dört metot) ve `FakePaymentProvider`;
  - `BahcedenProperties.payment.provider` (açılışta doğrulanır).
- [x] 1.3 `PayoutAccountService` yazılır (D2):
  - `requireApprovedSeller` kapısı;
  - oluşturma veya güncelleme;
  - aynı istekte `registerSubMerchant`;
  - sağlayıcı reddinde `PROVIDER_REJECTED` ve 422;
  - maskeleme yardımcısı.
  - Log'a IBAN ve kimlik numarası yazılmaz.
- [x] 1.4 DTO'lar `@Schema` açıklaması ve örneğiyle yazılır: `PayoutAccountRequest`, `PayoutAccountView` (maskeli). `PayoutAccountController`: `GET` ve `PUT /api/v1/sellers/me/payout-account`. Ek olarak:
  - `SecurityConfig` girdileri;
  - yeni `ApiErrorCode` değerleri;
  - doğrulama ve hata mesajları `messages*.properties`'e.
- [x] 1.5 `catalog` güncellenir:
  - `Product.dispatchDays` ve `ListingRequest.dispatchDays` (isteğe bağlı, 1–3; D3);
  - `SellerListingView` ve `ProductDetail`'e `dispatchDays`;
  - satın alınabilirlik koşulu tek yerde (D1);
  - `ProductSummary` ve `ProductDetail`'e `purchasable`.
  - Vitrin sorgu sayısı testi değişmeden yeşil kalır: ödeme hesabı durumu mevcut sorgulara join ile eklenir.
- [x] 1.6 `openapi.yaml` yeniden üretilir (`OpenApiContractTest`).
- [x] 1.7 **Test (`critical`):**
  - TCKN, VKN ve IBAN doğrulayıcıları: geçerli ve geçersiz örnekler, boşluklu IBAN, türle uyuşmayan numara;
  - ödeme hesabı: başarılı kayıt; 401; onaylı olmayan satıcıya 403; alan hataları; sağlayıcı reddi 422; yanıtta tam IBAN ve tam numaranın bulunmaması; güncelleme;
  - satın alınabilirlik: dört koşulun her biri ayrı ayrı `false` yapar;
  - `dispatchDays`: varsayılan 2, güncellemede korunur, 0 ve 4 için 400.
  - Mevcut ilan ve vitrin testleri yeşil kalır.
- [x] 1.8 **Doğrulama:** `./mvnw -B verify` temiz.
- [x] 1.9 `npm run gen:api` çalıştırılır. `features/payout` (api, bff, bileşenler) yazılır. `(seller)/seller/payout` sayfası: form (türe göre vergi dairesi alanı), maskeli görünüm, `loading`, `error`. Satıcı paneline "Ödeme bilgileri" bağlantısı eklenir. "Ürünlerim" ve ilan detayında eksik ödeme bilgisi uyarısı gösterilir. İlan formuna kargoya verme süresi (1–3, radyo) eklenir. Ürün detayında süre, satın alınabilirlik nedeni ve "Sepete ekle" yerinde durum metni gösterilir; buton Faz 2'de gelir. Metinler `tr.json` ve `en.json`'a eklenir.
- [x] 1.10 **Test (web):**
  - ödeme formu: doğrulama hatası, sunucu `fieldErrors` eşlemesi, başarı yolu, türe göre alan görünürlüğü;
  - ilan formunda süre;
  - ürün detayında satın alınamaz nedenleri;
  - BFF'nin CSRF başlığı olmadan reddetmesi.
- [x] 1.11 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 2 · Sepet, teslimat illeri ve adresler: `api` → sözleşme → `web`

Bu faz bittiğinde alıcı sepetini doldurur, kargo ücretini ve toplamı görür, adres kaydeder. Sipariş henüz yoktur.

- [x] 2.1 `common` güncellenir:
  - `TurkishProvinces` sabit kataloğu (81 il, plaka kodu ve ad);
  - `BahcedenProperties.commerce` (D7 anahtarları ve `delivery-provinces`) ve açılışta katalog doğrulaması;
  - `application.yaml` ve `api/.env.example` gerekiyorsa güncellenir; değerler secret değildir, varsayılanları yaml'dadır.
- [x] 2.2 `order` paketinde `OrderPricing` yazılır (D7). Kargo ücreti, komisyon, üst sınır ve hakediş saf hesaplarla yapılır.
- [x] 2.3 Sepet ve adres katmanı yazılır:
  - `Cart` ve `CartItem` entity'leri ve repository'leri;
  - satın alınabilirlik join'li tek sepet sorgusu;
  - `CartService`: okuma (satıcıya göre gruplama, `OrderPricing` ile tutarlar, `available` işareti), adet koyma, satır silme. Kurallar: adet 1–99 ve stok, en çok 50 satır, kendi ürünü yasak, satın alınamayan ürün yasak.
  - `Address` entity'si ve repository'si (`findByIdAndAccountId`), `AddressService`: en çok 10 adres, teslimat ili kontrolü, telefon normalleştirmesi (mevcut yardımcı yeniden kullanılır), `deliverable` işareti.
- [x] 2.4 DTO'lar ve controller'lar yazılır. Uçlar: `GET /api/v1/cart`, `PUT/DELETE /api/v1/cart/items/{productId}`, `GET /api/v1/delivery/provinces` (herkese açık), `GET/POST /api/v1/addresses`, `PUT/DELETE /api/v1/addresses/{id}`. Ayrıca `SecurityConfig`, hata kodları ve mesajlar güncellenir. `openapi.yaml` yeniden üretilir.
- [x] 2.5 **Test (`critical`):**
  - `OrderPricing`: eşiğin altı, eşik, eşiğin üstü; 245,50 TL örneği; komisyonun üst sınırı; yuvarlama sınırı (x,5 kuruş); büyük tutar.
  - Sepet:
    - 401;
    - ekleme ve adet değiştirme;
    - stok ve 1–99 sınırları;
    - 50 satır sınırı;
    - kendi ürünü;
    - satın alınamayan ürün;
    - sepetteki ürünün sonradan satın alınamaz olması;
    - fiyat değişikliğinin yansıması;
    - iki satıcılı gruplama ve toplamlar;
    - sorgu sayısı.
  - Adres:
    - başarılı kayıt;
    - teslimat dışı il;
    - başkasının adresi 404;
    - 10 adres sınırı;
    - listeden çıkan ilin `deliverable = false` olması.
- [x] 2.6 **Doğrulama:** `./mvnw -B verify` temiz.
- [x] 2.7 `npm run gen:api` çalıştırılır. Ardından:
  - `features/cart` ve `features/addresses` (api, bff, bileşenler);
  - ürün detayında adet seçimi ve "Sepete ekle" (oturum yoksa `next` ile giriş);
  - başlıkta sepet bağlantısı ve sayısı (sunucuda okunur);
  - `(shop)/cart`: satıcı grupları, kargo ücreti, "ücretsiz kargoya X kaldı", satın alınamayan satır, adet değiştirme ve silme;
  - `(account)/account/addresses`: liste ve form;
  - `loading`, `error` ve boş durumlar;
  - metinler.
- [x] 2.8 **Test (web):**
  - sepete ekleme: başarı, oturumsuz yönlendirme, sunucu hatası;
  - sepet ekranı: gruplar, tutarlar, satın alınamayan satır, boş sepet;
  - adres formu: doğrulama, il listesi, sunucu hataları;
  - BFF CSRF reddi.
- [x] 2.9 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 3 · Sipariş, ödeme ve siparişlerin görüntülenmesi: `api` → sözleşme → `web`

Bu faz bittiğinde alıcı `fake` ödemeyle sipariş verir. Stok düşer, e-postalar gider. Alıcı ve satıcı siparişleri görür.

- [x] 3.1 Sipariş modeli kurulur:
  - `Order`, `SellerOrder`, `OrderItem`, `OrderStatusChange` entity'leri;
  - durum enum'ları ve `SellerOrderTransition` (D6);
  - repository'ler: alıcı sorguları `buyer_account_id` ile, satıcı sorguları `seller_id` ile, liste uçları için fetch join;
  - `Payment` entity'si ve repository'si;
  - sipariş numarası üreticisi;
  - `ProductRepository`'de kilitli stok sorgusu (`PESSIMISTIC_WRITE`, `id` sırası).
- [x] 3.2 `CheckoutService` yazılır:
  - sipariş oluşturma (D6): `Idempotency-Key`, satır ve stok kontrolü, satıcıya bölme, `OrderPricing` ile tutarlar, adres kopyası;
  - ödeme başlatma;
  - ödeme tamamlama (D8): `verify`, kilitli stok düşümü, durum geçişleri, `dispatch_due_at`, sepetten silme; stok yetmezse tam iade ve `CANCELLED_OUT_OF_STOCK`; başarısızlık; süre dolumu; idempotent tekrar.
  - Ayrıca iş günü hesabı (`common`, D3) ve `RateLimitPolicy.ORDER_CREATE` eklenir.
- [x] 3.3 E-postalar `messages*.properties`'e eklenir: alıcıya sipariş onayı, satıcıya yeni sipariş. Gönderim transaction sonrasında yapılır, hata akışı kesmez. Log'a adres ve içerik yazılmaz.
- [x] 3.4 DTO'lar ve controller'lar yazılır:
  - `POST /api/v1/orders` (`Idempotency-Key` zorunlu) ve `POST /api/v1/orders/{orderNumber}/payment`;
  - `GET /api/v1/orders` ve `GET /api/v1/orders/{orderNumber}`;
  - `GET /api/v1/sellers/me/orders?status=` ve `GET /api/v1/sellers/me/orders/{id}`. Satıcı yanıtında adresten yalnızca il ve ilçe bulunur.
  - `SecurityConfig`, hata kodları ve mesajlar güncellenir. `openapi.yaml` yeniden üretilir.
- [x] 3.5 `012-dev-seed-cart-and-checkout.sql` yazılır (design.md Migration Plan 2) ve `DevSeedIsolationTest` kapsamına eklenir.
- [x] 3.6 **Test (`critical`):**
  - Sipariş oluşturma:
    - başarılı;
    - 401;
    - `Idempotency-Key` olmadan 400 ve aynı anahtarla tekrar;
    - satın alınamayan satır;
    - yetersiz stok;
    - teslimat dışı ve başkasının adresi;
    - tutarların siparişe yazılması ve sonradan fiyat veya yapılandırma değişikliğinden etkilenmemesi.
  - Ödeme:
    - başarılıda stok, durum, sepet ve e-postalar (iki dilde);
    - eşzamanlı iki ödemede son ürün: biri ödenir, diğeri tam iade; stok negatife düşmez;
    - başarısız ödeme;
    - süre dolumu (sabit `Clock`);
    - tekrar çağrı;
    - başkasının siparişi 404;
    - `fake` olmayan test sağlayıcısıyla başarısızlık ve iade dalları.
  - Görüntüleme:
    - alıcı yalnızca kendi siparişlerini görür;
    - satıcı yalnızca kendi ödenmiş satıcı siparişlerini görür;
    - satıcı yanıtında ad, telefon ve açık adres yoktur;
    - askıdaki satıcıya 403;
    - liste sorgu sayısı;
    - son tarihin hesabı (cuma + 2 → salı 23:59 Türkiye saati).
- [x] 3.7 **Doğrulama:** `./mvnw -B verify` temiz.
- [x] 3.8 `npm run gen:api` çalıştırılır. Ardından:
  - `features/orders` (api, bff, bileşenler);
  - `(shop)/checkout`: adres seçme veya ekleme, satıcı grupları, toplam, cayma bilgisi; ödemeye geçerken `Idempotency-Key` üretip deneme boyunca korur;
  - `(shop)/checkout/return`: ödemeyi tamamlar ve sonucu gösterir (başarılı, başarısız, süre doldu, stok yetersizliği nedeniyle iade);
  - `(account)/account/orders` ve `[number]`;
  - `(seller)/seller/orders` (durum sekmeleri) ve `[id]`;
  - `SessionNav`'a "Siparişlerim" ve satıcı için "Gelen siparişler";
  - dört durum ve metinler.
- [x] 3.9 **Test (web):**
  - checkout: adres yokken adres ekleme yönlendirmesi, sunucu hataları, `Idempotency-Key`'in tekrar denemede korunması;
  - dönüş ekranının dört sonucu;
  - sipariş listesi ve detay;
  - satıcı sipariş listesi ve detay (adres alanlarının yokluğu);
  - BFF CSRF reddi.
- [x] 3.10 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 4 · Kargodan önce iptal ve iade: `api` → sözleşme → `web`

Bu faz bittiğinde alıcı ve satıcı, kargoya verilmemiş satıcı siparişini iptal eder; para iade edilir, stok geri gelir.

- [x] 4.1 `Refund` entity'si ve repository'si yazılır. `OrderCancellationService` (D9):
  - alıcı iptali (gerekçe isteğe bağlı) ve satıcı iptali (gerekçe zorunlu, `requireApprovedSeller`);
  - tek transaction'da durum, stok geri ekleme ve sağlayıcıdan iade;
  - iade hatasında geri alma ve 502 `PAYMENT_PROVIDER_ERROR`;
  - geçmiş kaydı;
  - transaction sonrası e-posta.
- [x] 4.2 Uçlar `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/cancel` ve `POST /api/v1/sellers/me/orders/{id}/cancel` eklenir. İptal e-postaları (`mail.orderCancelledByBuyer.*`, `mail.orderCancelledBySeller.*`) yazılır. `openapi.yaml` yeniden üretilir.
- [x] 4.3 **Test (`critical`):**
  - alıcı iptali: iade tutarı, stok ve e-posta;
  - satıcı iptali: gerekçe zorunlu;
  - iki satıcılı siparişte diğer satıcı siparişinin etkilenmemesi;
  - iptal edilmiş veya ödenmemiş siparişte 409;
  - başkasının siparişi 404;
  - iade hatasında hiçbir şeyin değişmemesi;
  - geçmiş sırası;
  - son tarih geçmişken alıcı iptali.
- [x] 4.4 **Doğrulama:** `./mvnw -B verify` temiz.
- [x] 4.5 `web` tarafı: alıcı sipariş detayında "İptal et" (onay penceresi, isteğe bağlı gerekçe) ve satıcı detayında gerekçeli iptal (`ReasonDialog`). Süre aşımı bilgisi gösterilir. Metinler eklenir. Testler: iptal akışları ve sunucu hatası eşlemesi.
- [x] 4.6 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 5 · Uçtan uca doğrulama ve doküman

- [x] 5.1 `docker compose up --build` ile uçtan uca senaryo çalıştırılır:
  1. `uretici@ornek.test` ödeme bilgilerini günceller.
  2. İkinci bir test satıcısı ödeme hesabı olmadan kalır; ürünü vitrinde "satıcı hazır değil" olarak görünür.
  3. `alici@ornek.test` iki satıcıdan sepete ekler ya da tek satıcıyla eşik altını ve üstünü dener.
  4. Adres seçilir; teslimat dışı bir ilde (örneğin Bursa) adres kaydının reddi denenir.
  5. Sipariş oluşturulur ve `fake` ödeme tamamlanır.
  6. Stok düşer; mailpit'te alıcı ve satıcı e-postaları görünür.
  7. Satıcı siparişi görür; adres yalnızca il ve ilçedir.
  8. Alıcı iptal eder; stok geri gelir, iade kaydı oluşur, satıcıya e-posta gider.
  9. Aynı `Idempotency-Key` tekrarı yeni sipariş açmaz.
- [x] 5.2 Ekranların 375 ve 1440 px'de, açık ve koyu temada başsız tarayıcı ekran görüntüsü alınır: sepet, checkout, dönüş, siparişlerim, sipariş detayı, adresler, satıcı ödeme bilgileri, gelen siparişler, satıcı sipariş detayı, iptal pencereleri.
- [x] 5.3 Design D1–D10 → ADR-059…068 yazılır; `docs/adr/index.md` ve `mkdocs.yml` nav güncellenir.
- [x] 5.4 Referans belgeleri güncellenir:
  - `docs/reference/01-domain-model.md`: ER'ye on yeni tablo ve `products.dispatch_days`;
  - `02-api-reference.md`;
  - `03-design-system.md`, yeni bileşen varsa.
- [x] 5.5 Feature dosyaları açılır:
  - `docs/features/sepet-ve-siparis.md` ve `docs/features/odeme-ve-hakedis.md`, Geliştirmede durumunda;
  - `features/index.md`: tabloya eklenir, planlanan listeden çıkarılır;
  - `urun-ilanlari.md` ve `vitrin-ve-arama.md` Tarihçe satırları.
- [x] 5.6 Durum ve rehber belgeleri güncellenir:
  - `00-current-state.md`:
    - L1 değişir;
    - yeni sınırlamalar: `fake` sağlayıcı, aşırı satışta iade, IBAN ve TCKN düz metin, iş günü tatil bilmez, ödeme dönüşü gelmezse sipariş bekler;
    - test sayıları;
    - sıradaki adım: kargo gönderisi change'i.
  - `openspec/KALANLAR.md` A4: bölge kısmı ADR'ye bağlanır, mobil sorusu kalır.
  - `docs/how-to/yerel-gelistirme.md`: sipariş akışı, dev hesapları ve `fake` ödeme.
- [x] 5.7 Delta spec'ler ana spec'lere sync edilir:
  - yeni `seller-payout-details`, `cart-and-checkout` ve `orders`, her biri Purpose bölümüyle;
  - `product-listings` ve `storefront-catalog` MODIFIED/ADDED.
- [x] 5.8 `./docs/.tools/serve.sh --build` strict derleme temiz.
- [x] 5.9 Archive kullanıcı onayıyla yapıldı (2026-10-08). **İnsan işi, açık:**
  - hukuk teyidi: cayma bilgisi, mesafeli satış metni, aydınlatma (KALANLAR A5);
  - gerçek ödeme sağlayıcısı ve kargo entegratörü seçimi (KALANLAR A1, A2).

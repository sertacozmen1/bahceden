# add-cart-and-checkout

**Durum:** Onaylandı (2026-10-08, proje sahibi `/opsx:apply`), uygulandı ve kullanıcı onayıyla archive edildi (2026-10-08); PR ile `main`'e gidiyor.

> Bu change şu kararlar üzerine kurulur:
> - [ADR-054](../../../../docs/adr/054-split-payment-via-provider-port-provider-pending.md): bölünmüş ödeme, sağlayıcı arayüzün arkasında, `fake` ile.
> - [ADR-055](../../../../docs/adr/055-commission-percent-plus-fixed-from-seller.md): komisyon %5 + satıcı siparişi başına 3 TL.
> - [ADR-056](../../../../docs/adr/056-cancel-before-shipment-and-defect-refund.md): kargodan önce iptal tam iadedir.
> - [ADR-058](../../../../docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md): satıcı başına 99 TL kargo, 1000 TL ve üzeri ücretsiz, kargoya verme süresi ilanda beyan edilir.
>
> Ürün sahibinin 2026-10-08 tarihli kararları:
> - teslimat yalnızca İstanbul, İzmir, Ankara ve Manisa'ya (KALANLAR A4, bölge sorusu);
> - sepet sunucuda tutulur ve oturum gerektirir;
> - stok ödeme başarılı olunca düşer;
> - iş tek change olarak yapılır.

## Why

İlanlar yayında ama alıcı hiçbir şey satın alamıyor: sepet, adres, sipariş ve ödeme yok. Pazaryerinin değeri, alıcının üreticiden doğrudan satın alabilmesinde. Ödeme ve kargo kuralları ADR-054…058 ile karara bağlandı. Yalnızca sağlayıcı ve entegratör seçimi açık. Bu seçimler sipariş akışını değil, yalnızca canlıda gerçek ödeme almayı bekletiyor. Akış `fake` ödeme sağlayıcısıyla uçtan uca kurulabilir.

ADR-054'e göre satıcı, ödeme bilgilerini (IBAN, TCKN veya VKN, yasal ad) tamamlamadan ürünü satın alınamaz. Bu yüzden satıcının ödeme bilgileri bu change'in ön koşuludur ve change'in içindedir.

## What Changes

- **`api`, satıcı ödeme bilgileri (`payment` paketi, yeni):**
  - Onaylı satıcı ödeme bilgilerini girer ve günceller (`/api/v1/sellers/me/payout-account`): satıcı türü (bireysel, şahıs şirketi, şirket), yasal ad, TCKN veya VKN, vergi dairesi (şirket ve şahıs şirketi için), IBAN.
  - Kimlik numarası ve IBAN biçim ve sağlama denetiminden geçer.
  - Kayıt ödeme sağlayıcısında alt üye işyeri olarak açılır (`fake`).
  - Yanıtlar IBAN'ı ve kimlik numarasını yalnızca son dört haneyle gösterir.
- **`api`, satın alınabilirlik:**
  - Ürün satın alınabilir sayılır, eğer yayındaysa, satıcısı onaylıysa, satıcının ödeme hesabı etkinse ve stoğu varsa.
  - Vitrin listesine ve ürün detayına `purchasable` alanı eklenir. Satın alınamayan ürün vitrinde görünmeye devam eder.
- **`api`, kargoya verme süresi:**
  - İlana `dispatchDays` alanı eklenir (1–3 iş günü; istekte isteğe bağlı, varsayılan 2).
  - Ürün detayında görünür.
- **`api`, sepet (`order` paketi, yeni):**
  - Oturum açmış hesabın tek sepeti vardır: `GET /api/v1/cart`, `PUT /api/v1/cart/items/{productId}`, `DELETE /api/v1/cart/items/{productId}`.
  - Sepet satıcılara göre gruplanır. Ara toplamı, kargo ücretini ve genel toplamı sunucu hesaplar.
  - Satıcı kendi ürününü sepete ekleyemez.
- **`api`, teslimat adresi:**
  - Hesabın kayıtlı adresleri (en çok 10) oluşturulur, güncellenir ve silinir.
  - İl yalnızca teslimat yapılan iller listesinden seçilir: `GET /api/v1/delivery/provinces`. Liste yapılandırmada tutulur (İstanbul, İzmir, Ankara, Manisa).
- **`api`, sipariş:**
  - `POST /api/v1/orders` (`Idempotency-Key` başlığıyla) sepetten bir sipariş oluşturur. Sipariş satıcı siparişlerine bölünür.
  - Her satıcı siparişine o anki fiyatlar, kargo ücreti, komisyon ve satıcı hakedişi yazılır.
  - Teslimat adresi siparişe kopyalanır.
  - Ödeme sağlayıcıda başlatılır ve yanıt, alıcının yönlendirileceği ödeme adresini taşır.
- **`api`, ödeme tamamlama:**
  - `POST /api/v1/orders/{orderNumber}/payment` sağlayıcıdan sonucu doğrular.
  - Başarılıysa stok kilitli olarak düşer, sepetten satın alınan satırlar silinir, sipariş `PAID` olur ve satıcı siparişleri `NEW` olur.
  - Stok yetmezse ödemenin tamamı iade edilir ve sipariş iptal olur.
  - 30 dakikada tamamlanmayan ödeme süresi dolmuş sayılır.
- **`api`, sipariş görüntüleme:**
  - Alıcı kendi siparişlerini listeler ve detayını görür (`/api/v1/orders`).
  - Satıcı kendi satıcı siparişlerini listeler ve detayını görür (`/api/v1/sellers/me/orders`). Satıcı alıcının yalnızca il ve ilçesini görür.
- **`api`, iptal (ADR-056 §1–2):**
  - Kargoya verilmemiş satıcı siparişini alıcı iptal edebilir; satıcı da gerekçe yazarak iptal edebilir.
  - İptalde satıcı siparişinin tamamı (ürünler ve kargo ücreti) sağlayıcıdan iade edilir ve stok geri eklenir. Diğer tarafa hesabının dilinde e-posta gider.
- **`api`, e-postalar:** alıcıya sipariş onayı, satıcıya yeni sipariş bildirimi, karşı tarafa iptal bildirimi. Yalnızca e-posta gönderilir; SMS KALANLAR B4'e bağlı.
- **`web`, alıcı:**
  - Ürün detayında adet seçimi ve "Sepete ekle".
  - Başlıkta sepet bağlantısı ve sayısı.
  - `/cart`: satıcılara göre gruplu sepet, kargo ücreti, toplam.
  - `/checkout`: adres seçme veya ekleme, özet, "cayma hakkı yoktur" bilgisi, ödemeye geçiş.
  - `/checkout/return`: ödeme dönüşü ve sonuç ekranı.
  - `/account/orders` ve `/account/orders/[number]`: siparişlerim, detay, iptal.
  - `/account/addresses`: adreslerim.
- **`web`, satıcı:**
  - `/seller/payout`: ödeme bilgileri.
  - `/seller/orders` ve `/seller/orders/[id]`: gelen siparişler, detay, gerekçeli iptal.
  - İlan formunda kargoya verme süresi.
  - Ödeme bilgisi eksikse ilan ekranlarında uyarı.
- **Sözleşme:** yeni uçlar ve şemalar. `ProductSummary` ve `ProductDetail`'e `purchasable`, `ProductDetail`'e ve `SellerListingView`'a `dispatchDays` eklenir. `ListingRequest.dispatchDays` isteğe bağlıdır. Kırıcı değişiklik yok.

## Capabilities

- **Yeni:** `seller-payout-details` (delta `specs/seller-payout-details/spec.md`): satıcının ödeme bilgileri, doğrulama, maskeleme, alt üye kaydı.
- **Yeni:** `cart-and-checkout` (delta `specs/cart-and-checkout/spec.md`): sepet, teslimat illeri, adresler, sipariş oluşturma, fiyatlama, ödeme ve stok.
- **Yeni:** `orders` (delta `specs/orders/spec.md`): alıcı ve satıcı sipariş görüntüleme, kargo öncesi iptal ve iade, bildirimler, geçmiş.
- **Değişen:** `product-listings` (MODIFIED "Onaylı satıcı ilan oluşturur ve düzenler": kargoya verme süresi).
- **Değişen:** `storefront-catalog` (MODIFIED "Ürün detayı": satın alınabilirlik ve kargoya verme süresi; ADDED "Satın alınabilirlik").

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | Yeni `payment` paketi: `PaymentProvider` arayüzü, `FakePaymentProvider`, `SellerPayoutAccount`, `Payment`, `Refund`, servisler, IBAN ve kimlik numarası doğrulayıcıları. Yeni `order` paketi: `Cart`, `Address`, `Order`, `SellerOrder`, `OrderItem`, `OrderStatusChange`, `OrderPricing` (komisyon ve kargo; `critical`), `CheckoutService`, `OrderService`, controller'lar ve DTO'lar. `catalog`: `Product.dispatchDays`, `purchasable` hesabı, kilitli stok sorgusu. `common`: `BahcedenProperties.commerce` (komisyon, kargo, teslimat illeri, ödeme süresi), il kataloğu, iş günü hesabı. `SecurityConfig`, `RateLimitPolicy` (sipariş oluşturma), `ApiErrorCode`, mesajlar ve e-postalar |
| `web/` | Evet | `(shop)/cart`, `(shop)/checkout`, `(shop)/checkout/return`, `(account)/account/orders/*`, `(account)/account/addresses`, `(seller)/seller/payout`, `(seller)/seller/orders/*`; `features/cart`, `features/orders`, `features/payout`; ürün detayında sepete ekleme; başlıkta sepet; ilan formunda süre; BFF uçları; mesajlar; testler |
| DB | Evet | `011-cart-and-checkout.sql`: `products.dispatch_days` (varsayılan 2); `seller_payout_accounts`, `carts`, `cart_items`, `addresses`, `orders`, `seller_orders`, `order_items`, `order_status_changes`, `payments`, `refunds` tabloları ve indeksler. `012-dev-seed-cart-and-checkout.sql` (dev): `uretici@ornek.test` satıcısına sahte ödeme hesabı, `alici@ornek.test`'e İzmir'de sahte adres. Ekleyici; mevcut veri değişmez |
| `shared/` | Evet (ekleyici) | Yeni uçlar ve şemalar; mevcut şemalara isteğe bağlı ve yanıt alanları |
| `docs/` | Evet | D1–D10 → ADR-059…068; `docs/product/sepet-ve-siparis.md`; feature dosyaları `docs/features/sepet-ve-siparis.md` ve `odeme-ve-hakedis.md`; `urun-ilanlari.md` ve `vitrin-ve-arama.md` Tarihçe; `01-domain-model.md`, `02-api-reference.md`, `03-design-system.md`; `00-current-state.md`; `yerel-gelistirme.md`; KALANLAR A4 (bölge kısmı kapanır) |

## Bağımlılık

Yeni paket yok (K7). Ödeme sağlayıcısının yalnızca `fake` uygulaması yazılır; gerçek sağlayıcıya HTTP çağrısı bu change'te yok. IBAN mod-97 ve TCKN sağlama algoritmaları JDK ile yazılır.

## Kişisel veri (K10)

- **Yeni kişisel veri:**
  - satıcının IBAN'ı, TCKN veya VKN'si ve yasal adı;
  - alıcının teslimat adresi ve telefonu;
  - sipariş içeriği.
- **Satıcı ödeme verisi:** IBAN ve kimlik numarası API yanıtlarında yalnızca son dört haneyle döner ve log'a yazılmaz. Alt üye kaydı için ödeme sağlayıcısına gider (ADR-054, onaylı). Bu change'te sağlayıcı `fake` olduğu için dışarı veri gitmez.
- **Alıcının adresi:**
  - Yalnızca hesabın kendisi görür.
  - Satıcı yalnızca il ve ilçeyi görür; tam adres gönderi change'inde entegratöre gider (ADR-057).
  - Adres ve sipariş içeriği log'a yazılmaz.
- **Saklama:** sipariş kayıtları silinmez (§11.4). Saklama süresi ve aydınlatma metni KALANLAR A5'e bağlıdır; risk olarak notludur.
- Seed, fixture ve test verisi sahtedir: `ornek.test`, `+90 555 000 00 00`, `Ornek Mahallesi`, geçerli sağlamalı sahte TCKN ve IBAN.

## Non-goals

- Kargo gönderisi, etiket, durum takibi ve "kargoya verildi" / "teslim edildi" durumları (sonraki change, ADR-057).
- Ayıplı ürün talebi, kısmi iade ve admin hakemliği (sonraki change, ADR-056 §4–5).
- Gerçek ödeme sağlayıcısı entegrasyonu (KALANLAR A1); hakedişin satıcıya aktarım takibi ve vade ekranı.
- Misafir (oturumsuz) sepet ve satın alma.
- Stok rezervasyonu (stok ödeme anında düşer).
- Kupon, kampanya, asgari sepet tutarı, taksit.
- Admin sipariş ekranı.
- Fatura ve e-arşiv.
- SMS bildirimi (KALANLAR B4).
- Resmi tatil takvimi (iş günü hesabı yalnızca hafta sonunu dışlar).

## Riskler

- **Aşırı satış:** stok ödeme anında düştüğü için iki alıcı aynı son ürünü ödeyebilir. İkincinin ödemesi tamamen iade edilir. Alıcı açısından "para çekildi, iade edildi" deneyimi olur; ürün sahibi bunu kabul etti. Ölçülür; sık görülürse rezervasyon ayrı bir karar olur.
- **Ödeme dönüşü gelmezse:** alıcı ödeme sayfasından dönmezse sipariş `PENDING_PAYMENT` kalır. Ödeme tamamlanmadığı için stok veya para etkisi yoktur. Sağlayıcı bildirimi (webhook) gerçek sağlayıcıyla gelir.
- **Hassas veri düz metin:** IBAN ve kimlik numarası veritabanında şifresiz durur. Disk ve yedek şifrelemesi KALANLAR B5'e bağlıdır; uygulama düzeyi şifreleme bu change'te yok (D2).
- **Change büyük:** tek PR'da çok sayıda uç ve ekran var. Fazlar tek başına çalışır durumda bırakılır (K5) ve her faz kendi testiyle kapanır.
- **Hukuk:** "cayma hakkı yoktur" bilgisi, sipariş öncesi bilgilendirme ve mesafeli satış sözleşmesi metni hukuk teyidi bekliyor (KALANLAR A5). Ekranda bilgi metni gösterilir; sözleşme metni bu change'in kapsamında değil.

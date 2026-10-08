# Sepet ve Sipariş

**Durum:** Yayında
**İlk sürüm:** 2026-10-08
**Son değişiklik:** 2026-10-08

| | |
|---|---|
| **Ürün belgesi** | [Sepet ve Sipariş](../product/sepet-ve-siparis.md) |
| **Kararlar** | Çerçeve: [ADR-054](../adr/054-split-payment-via-provider-port-provider-pending.md) … [ADR-058](../adr/058-flat-shipping-fee-per-seller-and-declared-handover.md). Uygulama: [ADR-059](../adr/059-payment-and-order-packages-single-purchasability-rule.md), [ADR-061](../adr/061-listing-dispatch-days-business-days-weekends-only.md) … [ADR-068](../adr/068-checkout-web-routes-server-cart-count.md) |
| **Spec (capability)** | `openspec/specs/cart-and-checkout`, `openspec/specs/orders` (yeni); `product-listings` ve `storefront-catalog` (kargoya verme süresi, satın alınabilirlik) |
| **Change'ler** | `openspec/changes/archive/2026-10-08-add-cart-and-checkout` (archive edildi) |
| **Rehberler** | [Yerel geliştirme](../how-to/yerel-gelistirme.md) |
| **Referans** | [Domain modeli](../reference/01-domain-model.md), [API referansı](../reference/02-api-reference.md), [Tasarım sistemi](../reference/03-design-system.md) |
| **Kod** | `api/src/main/java/com/bahceden/order/` (`CartService`, `AddressService`, `CheckoutService`, `OrderService`, `OrderCancellationService`, `OrderPricing`), `web/src/app/(shop)/cart`, `web/src/app/(shop)/checkout`, `web/src/app/(account)/account/orders`, `web/src/app/(account)/account/addresses`, `web/src/app/(seller)/seller/orders`, `web/src/features/cart`, `web/src/features/addresses`, `web/src/features/orders` |

---

## Ne yapar

Alıcı ürün sayfasında adet seçip ürünü sepete ekler; sepet hesabına bağlıdır ve cihazlar arasında korunur. Sepet üreticilere göre gruplanır: her üretici ayrı paket gönderir, kargo ücreti üretici başına 99 TL'dir ve aynı üreticiden 1000 TL ve üzeri alışverişte ücretsizdir. Alıcı teslimat adresini kaydeder (bugün İstanbul, İzmir, Ankara ve Manisa), ödeme ekranında adresini seçer ve ödemeye geçer. Ödeme alınınca stok düşer; ürün o sırada tükendiyse para tamamen iade edilir. Alıcı siparişlerini ve her üreticinin kargoya verme son tarihini görür; üretici kargoya vermeden önce siparişi iptal edip parasını geri alabilir. Üretici gelen siparişlerini, komisyonu ve elime geçecek tutarı görür; gerekirse gerekçe yazarak iptal eder.

## Bilinçli sınırlar

- **Kargo gönderisi, etiket ve takip yok** (sonraki change; [ADR-057](../adr/057-shipping-via-integrator-port-integrator-pending.md)). Satıcı siparişi kargoya verilene kadar "Hazırlanıyor" kalır.
- **Ayıplı ürün iadesi ve kısmi iade yok** (sonraki change; ADR-056 §4–5).
- **Ödeme sahte sağlayıcıyla** çalışır; gerçek sağlayıcı seçilmedi (KALANLAR A1). Canlıda ödeme alınamaz.
- **Misafir sepeti yok**; sepete eklemek giriş ister.
- **Stok rezervasyonu yok**; son ürünü iki alıcı ödeyebilir, ikincisi tam iade alır.
- **Teslimat dört ille sınırlı** ([ADR-063](../adr/063-delivery-provinces-config-address-book.md)); ilçe doğrulanmaz.
- **İş günü hesabı resmi tatilleri bilmez** ([ADR-061](../adr/061-listing-dispatch-days-business-days-weekends-only.md)).
- **Admin sipariş ekranı, kupon, asgari sepet, fatura yok.**

## Açık konular

- Mesafeli satış sözleşmesi, ön bilgilendirme ve cayma metni hukuk teyidi bekliyor (KALANLAR A5).
- Gerçek cihazda insan doğrulaması. Başsız Chrome ile 375 / 1440 px ve iki temada 48 ekran görüntüsü alındı.
- Ödemesi hiç tamamlanmayan siparişler `PENDING_PAYMENT` kalır; temizlik işi yok.
- Sipariş için e-posta doğrulama kapısı kararı (`00-current-state` L11).

## Tarihçe

- 2026-10-08: `add-cart-and-checkout` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-08-add-cart-and-checkout/`). Ön koşullar: delta spec'ler ana spec'lere sync edildi; D1–D10 → ADR-059…068; dokümanlar güncel; `api` 237 ve `web` 194 test yeşil. Taşımada göreli linkler bir seviye uzatıldı. Açık ve notlu: sipariş için e-posta doğrulama kapısı (`00-current-state` L11), hukuk teyidi (KALANLAR A5), gerçek cihaz doğrulaması.
- 2026-10-08: İlk sürüm (change `add-cart-and-checkout`).
  - `api` (`order` paketi):
    - sepet (`/api/v1/cart`): satıcıya göre gruplama, kargo ücreti, güncel fiyat, satın alınamaz satır işareti;
    - teslimat illeri ve adres defteri (`/api/v1/delivery/provinces`, `/api/v1/addresses`);
    - sipariş (`POST /api/v1/orders`, `Idempotency-Key` zorunlu), ödeme tamamlama, kilitli stok düşümü, stok yetmezse tam iade, süre dolumu;
    - alıcı ve satıcı sipariş görüntüleme; satıcıya alıcının yalnızca il ve ilçesi;
    - kargodan önce iptal (alıcı, satıcı gerekçeyle), tam iade, stok geri ekleme;
    - sipariş ve iptal e-postaları (hesap dilinde, transaction sonrasında).
  - Şema: `011-cart-and-checkout.sql`, `012-dev-seed-cart-and-checkout.sql`.
  - `web`: `/cart`, `/checkout`, `/checkout/return`, `/account/orders`, `/account/orders/[number]`, `/account/addresses`, `/seller/orders`, `/seller/orders/[id]`; ürün detayında "Sepete ekle"; başlıkta sepet ve adet rozeti.
  - Ortaklaştırılan bileşenler: `SectionTabs`, `FilterTabs`, `QuantityStepper`, `CartGroups`, `BuyerOrderView`; `ReasonDialog`'a isteğe bağlı gerekçe; telefon normalleştirmesi `common/text/Phones`'a.
  - Testler: 237 `api` (+75), 194 `web` (+46).
  - Doğrulama: `docker compose` üzerinde 28 adımlı uçtan uca senaryo (ödeme bilgisi, sepet, teslimat dışı il reddi, sipariş, tekrar anahtarı, web dönüşü, stok, e-postalar, satıcı görünümü, iptal ve iade).
  - Kararlar: ADR-059…068; KALANLAR A4'ün teslimat bölgesi sorusu ADR-063 ile kapandı.

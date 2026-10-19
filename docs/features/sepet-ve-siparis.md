# Sepet ve Sipariş

**Durum:** Yayında
**İlk sürüm:** 2026-10-08
**Son değişiklik:** 2026-10-20

| | |
|---|---|
| **Ürün belgesi** | [Sepet ve Sipariş](../product/sepet-ve-siparis.md) |
| **Kararlar** | Çerçeve: [ADR-054](../adr/054-split-payment-via-provider-port-provider-pending.md) … [ADR-058](../adr/058-flat-shipping-fee-per-seller-and-declared-handover.md). Uygulama: [ADR-059](../adr/059-payment-and-order-packages-single-purchasability-rule.md), [ADR-061](../adr/061-listing-dispatch-days-business-days-weekends-only.md) … [ADR-068](../adr/068-checkout-web-routes-server-cart-count.md), e-posta doğrulaması [ADR-093](../adr/093-order-requires-verified-email.md) … [ADR-095](../adr/095-dev-buyer-verified-by-new-seed.md), temizlik [ADR-096](../adr/096-unpaid-orders-closed-by-scheduled-sweep.md) |
| **Spec (capability)** | `openspec/specs/cart-and-checkout`, `openspec/specs/orders` (yeni); `product-listings` ve `storefront-catalog` (kargoya verme süresi, satın alınabilirlik) |
| **Change'ler** | `openspec/changes/archive/2026-10-08-add-cart-and-checkout` (archive edildi), `openspec/changes/archive/2026-10-19-add-checkout-email-verification` (archive edildi), `openspec/changes/archive/2026-10-20-add-stale-record-cleanup` (archive edildi) |
| **Rehberler** | [Yerel geliştirme](../how-to/yerel-gelistirme.md) |
| **Referans** | [Domain modeli](../reference/01-domain-model.md), [API referansı](../reference/02-api-reference.md), [Tasarım sistemi](../reference/03-design-system.md) |
| **Kod** | `api/src/main/java/com/bahceden/order/` (`CartService`, `AddressService`, `CheckoutService`, `OrderService`, `OrderCancellationService`, `OrderPricing`), `web/src/app/(shop)/cart`, `web/src/app/(shop)/checkout`, `web/src/app/(account)/account/orders`, `web/src/app/(account)/account/addresses`, `web/src/app/(seller)/seller/orders`, `web/src/features/cart`, `web/src/features/addresses`, `web/src/features/orders` |

---

## Ne yapar

Alıcı ürün sayfasında adet seçip ürünü sepete ekler; sepet hesabına bağlıdır ve cihazlar arasında korunur. Sepet üreticilere göre gruplanır: her üretici ayrı paket gönderir, kargo ücreti üretici başına 99 TL'dir ve aynı üreticiden 1000 TL ve üzeri alışverişte ücretsizdir. Alıcı teslimat adresini kaydeder (bugün İstanbul, İzmir, Ankara ve Manisa), ödeme ekranında adresini seçer ve ödemeye geçer; sipariş vermek için e-posta adresinin doğrulanmış olması gerekir, değilse ödeme ekranı yeni doğrulama bağlantısı istemeyi sunar. Ödeme alınınca stok düşer; ürün o sırada tükendiyse para tamamen iade edilir. Alıcı siparişlerini ve her üreticinin kargoya verme son tarihini görür; üretici kargoya vermeden önce siparişi iptal edip parasını geri alabilir. Üretici gelen siparişlerini, komisyonu ve elime geçecek tutarı görür; gerekirse gerekçe yazarak iptal eder.

## Bilinçli sınırlar

- **Kargo ve ayıplı ürün ayrı feature'lardır:** [Kargo ve Teslimat](kargo-ve-teslimat.md), [Ayıplı Ürün ve İade](ayipli-urun-ve-iade.md).
- **Doğrulanmamış e-postayla sipariş verilemez** ([ADR-093](../adr/093-order-requires-verified-email.md)); sepet ve adresler doğrulama istemez. Doğrulama sayfası alıcıyı ödemeye geri götürmez.
- **Ödeme sahte sağlayıcıyla** çalışır; gerçek sağlayıcı seçilmedi (KALANLAR A1). Canlıda ödeme alınamaz.
- **Misafir sepeti yok**; sepete eklemek giriş ister.
- **Stok rezervasyonu yok**; son ürünü iki alıcı ödeyebilir, ikincisi tam iade alır.
- **Teslimat dört ille sınırlı** ([ADR-063](../adr/063-delivery-provinces-config-address-book.md)); ilçe doğrulanmaz.
- **İş günü hesabı resmi tatilleri bilmez** ([ADR-061](../adr/061-listing-dispatch-days-business-days-weekends-only.md)).
- **Admin sipariş ekranı, kupon, asgari sepet, fatura yok.**
- **Ödenmemiş sipariş için e-posta gitmez**; iş onu kapatır, alıcı listesinde durumu görür ([ADR-096](../adr/096-unpaid-orders-closed-by-scheduled-sweep.md)).

## Açık konular

- Mesafeli satış sözleşmesi, ön bilgilendirme ve cayma metni hukuk teyidi bekliyor (KALANLAR A5).
- Gerçek cihazda insan doğrulaması. Başsız Chrome ile 375 / 1440 px ve iki temada 48 ekran görüntüsü alındı.

## Tarihçe

- 2026-10-20: `add-stale-record-cleanup` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-20-add-stale-record-cleanup/`). Ön koşullar: delta spec'ler (`cart-and-checkout`, `account-identity`) ana spec'lere sync edildi; D1–D2 → ADR-096, ADR-097; dokümanlar güncel; `api` 338 ve `web` 277 test yeşil.
- 2026-10-20: Ödeme dönüşü gelmeyen siparişler kapanır (change `add-stale-record-cleanup`). Zamanlanmış iş, ödeme süresi 1 saatten önce dolmuş `PENDING_PAYMENT` siparişleri sağlayıcıdan doğrular ve dönüşle aynı kuralla sonuçlandırır: geç alınmış ödeme tam iade edilir, sipariş `PAYMENT_EXPIRED` ya da `PAYMENT_FAILED` olur; yapan taraf sistemdir. `016` kısmi indeksi. Karar ADR-096. L32 kapandı.
- 2026-10-19: `add-checkout-email-verification` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-19-add-checkout-email-verification/`). Ön koşullar: delta spec `cart-and-checkout` ana spec'ine sync edildi; D1–D3 → ADR-093…095; dokümanlar güncel; `api` 327 ve `web` 277 test yeşil.
- 2026-10-19: Sipariş için e-posta doğrulaması (change `add-checkout-email-verification`). `api`: `POST /orders` doğrulanmamış hesapta `422 ORDER_EMAIL_NOT_VERIFIED`; tekrar anahtarı kapıdan önce; durum güncel kayıttan. `web`: ödeme sayfası form yerine doğrulama paneli ve yeni bağlantı düğmesi gösterir. Dev: `015` seed'i `alici@ornek.test`'i doğrulanmış yapar. Kararlar ADR-093…095. Bilinçli sınırlardaki eskimiş "kargo yok" ve "ayıplı ürün yok" maddeleri düzeltildi.
- 2026-10-14: Alıcının ve satıcının sipariş detayına talep bölümü eklendi; alıcı teslimden sonraki 48 saat içinde **Sorun bildir** ile talep açar. Satıcı siparişinin durumu talepten etkilenmez, sonucu talep taşır. Ayrıntı: [Ayıplı Ürün ve İade](ayipli-urun-ve-iade.md) (change `add-defect-claims`, ADR-079, ADR-086).
- 2026-10-10: Satıcı siparişi kargo durumlarını kazandı (`SHIPPED`, `DELIVERED`, `DELIVERY_FAILED`, `REFUNDED`, `CLOSED`); gönderisi açık siparişin iptali önce gönderiyi iptal eder; kargoya verilmiş sipariş iptal edilemez. Ayrıntı: [Kargo ve Teslimat](kargo-ve-teslimat.md) (change `add-shipments`, ADR-072, ADR-076).
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

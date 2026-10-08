# [ADR-068] `web`: sepet ve ödeme vitrinde, siparişler hesapta, satıcı ekranları satıcı panelinde; sepet sayısı başlıkta sunucudan

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D10

---

## Context

Sepet, ödeme, sipariş ve satıcı ödeme bilgileri için yeni ekranlar gerekiyor. Başlık dar ekranda zaten kalabalık. Token tarayıcıya verilmez ([ADR-023](023-web-bff-cookies-and-header-csrf.md)).

## Decision

- **Rotalar:** vitrin `(shop)`: `/cart`, `/checkout`, `/checkout/return`; hesap `(account)`: `/account/orders`, `/account/orders/[number]`, `/account/addresses`; satıcı `(seller)`: `/seller/payout`, `/seller/orders`, `/seller/orders/[id]`. Hepsi oturum ister; satıcı ekranları satıcı rolü.
- **Başlık:** oturumlu her hesapta sepet bağlantısı ve adet rozeti; sayı sunucuda okunur, değişiklikten sonra sayfa yenilenir. Dar ekranda "Çıkış yap" yalnızca ikon olur (375 px'de yatay kaydırma olmaz).
- **Satıcı paneli** sekmeleri: Ürünlerim · Gelen siparişler · Ödeme bilgileri (başlığa yeni bağlantı eklenmez). Ödeme hesabı etkin değilse ilan ekranlarında uyarı gösterilir.
- **Ödeme ekranı:** adres seçme veya ekleme, satıcılara göre salt okunur özet, toplam ve "taze ürünlerde teslimattan sonra cayma hakkı yoktur" bilgisi; tekrar anahtarı ekran açılınca bir kez üretilir ve aynı ekrandaki denemelerde korunur; ödeme sayfasına tam sayfa yönlendirme yapılır.
- **Dönüş ekranı** sunucuda ödemeyi tamamlar ve sonucu açıkça söyler (ödendi, başarısız, süresi doldu, stok yetmediği için iade, bekliyor).
- **Ortak bileşenler (K9):** `SectionTabs` (admin ve satıcı sekmeleri), `FilterTabs` (admin listeleri ve satıcı siparişlerinin durum süzgeci), `QuantityStepper`, `CartGroups` (sepet ve ödeme özeti), `BuyerOrderView` (sipariş detayı ve dönüş), `ReasonDialog` (isteğe bağlı gerekçe seçeneğiyle), `AddressForm` (adres defteri ve ödeme).
- Para ve tarih yalnızca `money.ts` ve `datetime.ts` ile biçimlenir; ekran hesap yapmaz.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| İstemci tarafı sepet deposu | İki doğruluk kaynağı ve K7 kararı. |
| Tek sayfalık checkout sihirbazı | Adres defteri zaten ayrı ekran; adres, özet, ödeme ayrımı alışılmış desen. |
| Satıcı bağlantılarını başlığa eklemek | 375 px'de başlık taşar. |
| Dönüşte istemci tarafı tamamlama | JavaScript'e bağımlı ve iki istek; sunucu tarafında tek ve tekrarlanabilir çağrı yeterli. |

## Consequences

### Olumlu
- Satıcı paneli tek yerde; başlık dar ekranda sığar.
- Ödeme ve iptal ekranları ortak bileşenlerle tutarlı.

### Olumsuz
- Dönüş sayfasını açmak (GET) durum değiştiren bir çağrı tetikler; çağrı tekrarlanabilir olduğu için güvenlidir ama alışılmış desen değildir.

### Nötr / Trade-off
- "Ayıplı ürünü 48 saat içinde bildirebilirsiniz" cümlesi ayıplı ürün akışıyla birlikte eklenecek.

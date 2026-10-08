# [ADR-059] `payment` ve `order` paketleri açılır; satın alınabilirlik tek bir koşuldur ve vitrine alan olarak çıkar

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D1

---

## Context

Sepet, sipariş ve ödeme ilk kez yazılıyor. `AGENTS.md` §11.2 domain paketlerini sayıyor (`order`, `payment`) ama paketler arasındaki yön ve "bir ürün satın alınabilir mi" kuralının yeri belirsizdi. [ADR-054](054-split-payment-via-provider-port-provider-pending.md), ödeme bilgisi eksik satıcının ürününün satın alınamayacağını söylüyor. Vitrinde bugün hesapsız seed satıcılarının ve ödeme bilgisini henüz girmemiş satıcıların ürünleri var.

## Decision

- **`com.bahceden.payment`:** satıcının ödeme hesabı, ödeme sağlayıcısı arayüzü ve `fake` uygulaması, ödeme ve iade kayıtları.
- **`com.bahceden.order`:** sepet, adresler, sipariş, satıcı siparişi, satırlar, fiyatlama, iptal.
- **Yön:** `order` → `payment`, `catalog`; `catalog` → `payment` yalnızca satın alınabilirlik için (SQL alt sorgusu); `payment` → `seller`. `payment` siparişi, `catalog` siparişi bilmez. Döngü yoktur.
- **Satın alınabilirlik** tek yerde, `Product.isPurchasable()`: yayında **ve** satıcısı `APPROVED` **ve** satıcının ödeme hesabı `ACTIVE` **ve** stok > 0. Ödeme hesabının durumu ürün satırıyla aynı sorguda bir `@Formula` alt sorgusuyla okunur; vitrine ek sorgu eklenmez.
- Vitrin listesi ve ürün detayı `purchasable` alanını taşır. Satın alınamayan ürün vitrinde **görünmeye devam eder**; ekranda "Tükendi" veya "Satıcı henüz satışa hazır değil" yazar. Sepet ve sipariş aynı koşulu kullanır.
- Sepete ekleme ürün kimliğiyle yapıldığı için ürün detayına `id` eklenir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Ödeme hesabı olmayan satıcının ürününü vitrinden gizlemek | Vitrin bugünkü haliyle boşalır; görünürlük kuralı ikinci kez değişir. |
| Kontrolü yalnızca siparişte yapmak | Alıcı ödeme adımında hata alır; ekranın bunu baştan bilmesi gerekir. |
| Tek bir `commerce` paketi | §11.2'deki domain ayrımına aykırı; sağlayıcı kodu siparişle iç içe geçer. |
| Ödeme hesabı durumunu ayrı sorguyla okumak | Vitrin sorgu sayısı artar (≤ 3 sınırı, [ADR-052](052-storefront-cover-second-query-detail-by-slug.md)). |

## Consequences

### Olumlu
- Vitrin, sepet ve sipariş aynı kararı verir; ekran baştan doğru eylemi gösterir.
- Vitrin sorgu sayısı değişmedi.

### Olumsuz
- `catalog` entity'si `payment` tablosunu SQL düzeyinde bilir (`@Formula`); tablo adı değişirse formül de değişmelidir.
- Ürün detayında sıralı ürün kimliği herkese açıktır (gizli değildir; yazma uçları oturum ve satın alınabilirlik ister).

### Nötr / Trade-off
- Satın alınabilirlik vitrinde kart düzeyinde gösterilmez; neden ürün detayında söylenir.

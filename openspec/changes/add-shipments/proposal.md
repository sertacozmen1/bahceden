# add-shipments

**Durum:** Onaylandı (2026-10-10, proje sahibi "uygula") ve uygulandı; archive kullanıcı onayı bekliyor (`AGENTS.md` §5.3).

> Bu change şu kararlar üzerine kurulur:
> - [ADR-057](../../../docs/adr/057-shipping-via-integrator-port-integrator-pending.md): gönderi kargo entegratörü API'siyle açılır; entegratör bir arayüzün arkasında, `fake` ile.
> - [ADR-058](../../../docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md): ücret ve kargoya verme süresi (bu change ücrete dokunmaz).
> - [ADR-056](../../../docs/adr/056-cancel-before-shipment-and-defect-refund.md) §1–2: kargoya verilmeden önce iptal tam iadedir.
>
> Ürün sahibinin 2026-10-10 tarihli kararları:
> - kapsam yalnızca gönderi: açma, etiket, durum, teslim. Ayıplı ürün talebi (ADR-056 §4–5) sonraki change'tir;
> - satıcı paketi **şubeye teslim eder**; satıcıdan açık adres toplanmaz, gönderici olarak satıcının adı, ili ve telefonu gider;
> - "teslim edilemedi" durumunda **admin karar verir**: tam iade veya gerekçeyle kapatma.

## Why

Alıcı ödeme yapabiliyor ama sipariş "Hazırlanıyor"da kalıyor (`00-current-state.md` L1): satıcının paketi gönderecek bir yolu, alıcının da paketini izleyecek bir yeri yok. Kargo bildirimi, ürün eklemeyle birlikte satıcının en kritik akışıdır (§12.5). Entegrasyon biçimi ADR-057 ile karara bağlandı; yalnızca entegratör seçimi açık (KALANLAR A2). Bu seçim akışı değil, yalnızca canlıda gerçek gönderi açmayı bekletiyor. Akış `fake` entegratörle uçtan uca kurulabilir. Teslim tarihi ayrıca bir sonraki dilimin (ayıplı ürün, 48 saat) ön koşuludur.

## What Changes

- **`api`, `shipping` paketi (yeni):**
  - `ShippingIntegrator` arayüzü: gönderi oluştur, etiket al, durum sorgula, gönderi iptal et. Tek uygulama `FakeShippingIntegrator` (dışarı çıkmaz, K10).
  - `shipments` tablosu: satıcı siparişine bağlı gönderi, kargo firması, takip numarası, durum, kargoya verilme ve teslim zamanları.
- **`api`, gönderi açma (satıcı):**
  - `POST /api/v1/sellers/me/orders/{id}/shipment` — onaylı satıcı `NEW` durumundaki kendi satıcı siparişi için koli boyutunu (küçük, orta, büyük) seçerek gönderi açar. Tekrarlanan istek mevcut gönderiyi döndürür.
  - `GET /api/v1/sellers/me/orders/{id}/shipment/label` — etiket yalnızca sahibine, korumalı başlıklarla sunulur.
  - Satıcı siparişi detayına gönderi bilgisi eklenir (firma, takip numarası, durum).
- **`api`, gönderi durumu:**
  - Zamanlanmış bir iş açık gönderilerin durumunu entegratörden sorgular. Durum ilerledikçe satıcı siparişi `SHIPPED` (kargoya verildi), `DELIVERED` (teslim edildi) veya `DELIVERY_FAILED` (teslim edilemedi) olur. Geçişler sipariş geçmişine yazılır.
  - `fake` entegratör durumu zamana göre ilerletir (geliştirmede birkaç dakika; süreler yapılandırmada).
- **`api`, iptal (değişiklik):** kargoya verilmemiş satıcı siparişinde gönderi açılmışsa iptal önce gönderiyi entegratörde iptal eder, sonra tam iade yapar. Kargoya verilmiş sipariş iptal edilemez.
- **`api`, teslim edilemeyen sipariş (admin):**
  - `GET /api/v1/admin/seller-orders?status=DELIVERY_FAILED` ve detay.
  - `POST /api/v1/admin/seller-orders/{id}/resolution` — `REFUND` (ürün + kargo tam iade, stok geri eklenmez) veya `CLOSE` (gerekçe zorunlu, para hareketi yok).
- **`api`, alıcı:** sipariş detayında her satıcı siparişinin gönderi bilgisi (firma, takip numarası, durum, kargoya verilme ve teslim zamanı).
- **`api`, e-postalar:** alıcıya "kargoya verildi"; alıcıya ve satıcıya "teslim edilemedi"; admin kararı alıcıya ve satıcıya. Yalnızca e-posta; SMS KALANLAR B4'e bağlı.
- **`web`:**
  - Satıcı sipariş detayı: "Kargoya hazırla" (koli boyutu seçimi), etiket önizleme ve yazdırma, büyük puntoyla gönderi kodu (telefonla şubeye giden satıcı için), gönderi durumu.
  - Satıcı sipariş listesi: yeni durumlarla süzme.
  - Alıcı sipariş detayı: kargo firması, takip numarası, gönderi durumu.
  - Admin: `/admin/orders` teslim edilemeyen satıcı siparişleri listesi ve karar diyaloğu.
  - Etiket için BFF Route Handler (`/api/seller/orders/[id]/label`).
- **Sözleşme:** yeni uçlar ve alanlar; `SellerOrderStatus` enum'una yeni değerler (ekleyici, kırıcı değil).

### Non-goals

- Ayıplı ürün talebi, 48 saat süresi, kısmi iade (ADR-056 §4–5): sonraki change.
- Gerçek entegratör uygulaması, webhook ve imza doğrulaması (KALANLAR A2).
- Desi / bölgeye göre ücret; kargo ücreti ADR-058'deki gibi kalır. Koli boyutu yalnızca entegratöre iletilir.
- Satıcıdan açık gönderici adresi, kapıdan alım.
- Birden fazla koli; bir satıcı siparişi tek koli.
- Teslim sonrası satıcı hakedişinin sağlayıcıda serbest bırakılması (sağlayıcıya bağlı, KALANLAR A1).
- SMS bildirimi (B4), "teslim edildi" e-postası.
- Etiketlerin silinmesi / saklama süresi (KALANLAR A5).

## Capabilities

### New Capabilities

- `shipments`: gönderi açma, etiket, durum takibi, teslim edilemeyen siparişte admin kararı.

### Modified Capabilities

- `orders`: alıcının sipariş detayı gönderi bilgisini içerir; iptal açılmış gönderiyi de iptal eder ve kargoya verilmiş siparişte yapılamaz.

## Impact

- **`api`:** yeni `shipping` paketi; `order` paketinde `SellerOrderStatus`, `SellerOrderTransition`, `OrderCancellationService`, `OrderService`, `OrderNotifier`, DTO'lar; `payment` paketinde `RefundReason.UNDELIVERED`; `BahcedenProperties.shipping`; `SecurityConfig`; `messages*.properties`; e-posta şablonları; `@EnableScheduling`.
- **DB:** `013-shipments.sql`: `shipments` tablosu, `seller_orders` / `order_status_changes` / `refunds` CHECK kısıtlarının genişletilmesi. Ekleyici; veri kaybı yok.
- **`web`:** satıcı sipariş detayı ve listesi, alıcı sipariş detayı, yeni admin ekranı, etiket Route Handler'ı, `messages/tr.json` + `en.json`.
- **Sözleşme:** yeni uçlar ve alanlar, enum genişlemesi; kırıcı değişiklik yok.
- **Kişisel veri:** alıcının adı, telefonu ve teslimat adresi entegratöre gider (ADR-057 §4 ile onaylı; `fake` olduğu için bu change'te dışarı çıkmaz). Etiket bu bilgileri içerir; yalnızca satıcısına korumalı uçtan sunulur. Takip numarası ve adres uygulama log'una yazılmaz.
- **Bağımlılık:** yeni paket yok (K7). Zamanlama Spring'in kendi `@Scheduled`'ı; etiket görseli JDK Java2D/ImageIO.
- **Doküman:** `docs/product/kargo-ve-teslimat.md`, `docs/features/kargo-ve-teslimat.md` (yeni), ADR-071…, `docs/reference/` API ve ER sayfaları, `00-current-state.md` (L1 değişir).

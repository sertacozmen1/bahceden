# Kargo ve Teslimat

**Durum:** Yayında
**İlk sürüm:** 2026-10-10
**Son değişiklik:** 2026-10-27

| | |
|---|---|
| **Ürün belgesi** | [Kargo ve Teslimat](../product/kargo-ve-teslimat.md) |
| **Kararlar** | Çerçeve: [ADR-057](../adr/057-shipping-via-integrator-port-integrator-pending.md), [ADR-058](../adr/058-flat-shipping-fee-per-seller-and-declared-handover.md), [ADR-056](../adr/056-cancel-before-shipment-and-defect-refund.md). Uygulama: [ADR-071](../adr/071-shipping-package-integrator-port-fake-only.md) … [ADR-078](../adr/078-shipping-notifications-email-only-three-events.md), teslim e-postası [ADR-091](../adr/091-delivered-email-to-buyer-with-claim-deadline.md), [ADR-092](../adr/092-email-datetime-turkey-time-fixed-pattern.md), iptal reddinde durum eşitleme [ADR-105](../adr/105-cancel-refusal-reads-shipment-status-409-when-shipped.md), [ADR-106](../adr/106-cancel-dialog-keeps-message-refreshes-on-close.md) |
| **Spec (capability)** | `openspec/specs/shipments` (yeni), `openspec/specs/orders` |
| **Change'ler** | `openspec/changes/archive/2026-10-10-add-shipments` (archive edildi), `openspec/changes/archive/2026-10-19-add-delivered-email` (archive edildi), `openspec/changes/archive/2026-10-27-change-cancel-rejection-syncs-shipment` (archive edildi) |
| **Rehberler** | [Yerel geliştirme](../how-to/yerel-gelistirme.md) |
| **Referans** | [Domain modeli](../reference/01-domain-model.md), [API referansı](../reference/02-api-reference.md) |
| **Kod** | `api/src/main/java/com/bahceden/shipping/` (`ShipmentService`, `ShipmentStatusPoller`, `FakeShippingIntegrator`), `api/src/main/java/com/bahceden/order/` (`ShipmentProgressService`, `UndeliveredOrderService`), `web/src/features/orders/components` (`PrepareShipment`, `ShipmentCard`), `web/src/app/(admin)/admin/orders` |

---

## Ne yapar

Üretici ödenmiş siparişi hazırladığında satıcı panelinden koli boyutunu seçip gönderi açar; sistem kargo entegratöründen etiketi ve gönderi kodunu alır. Üretici etiketi yazdırır ya da kodu şubede söyler ve paketi şubeye bırakır. Gönderinin durumu kendiliğinden güncellenir: alıcı kargoya verildiğinde ve teslim edildiğinde e-posta alır (teslim e-postası sorun bildirimi için son anı da söyler), sipariş sayfasında firmayı, takip numarasını ve durumu görür. Teslim edilemeyen paketlerde admin tam iade ya da gerekçeyle kapatma kararı verir.

## Bilinçli sınırlar

- **Kargo entegratörü sahte** (`fake`); gerçek entegratör seçilmedi (KALANLAR A2). Canlıda gönderi açılamaz.
- **Ayıplı ürün talebi** ayrı feature'dır: [Ayıplı Ürün ve İade](ayipli-urun-ve-iade.md); teslim zamanı talep penceresini başlatır.
- **Satıcıya "teslim edildi" e-postası ve süre bitmeden hatırlatma yok** ([ADR-091](../adr/091-delivered-email-to-buyer-with-claim-deadline.md)).
- **Kapıdan alım ve çoklu koli yok**; paket şubeye teslim edilir.
- **SMS yok**; bildirimler e-posta (KALANLAR B4).

## Açık konular

- Etiketlerin saklama süresi (KALANLAR A5).
- Gerçek entegratörün durum bildirme biçimi (webhook / sorgu) ve şubede kodla kabul (KALANLAR A2).

## Tarihçe

- 2026-10-27: `change-cancel-rejection-syncs-shipment` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-27-change-cancel-rejection-syncs-shipment/`). Ön koşullar: delta spec `orders` ana spec'ine sync edildi; D1–D2 → ADR-105, ADR-106; dokümanlar güncel; `api` 359 ve `web` 317 test yeşil.
- 2026-10-26: İptal reddinde gönderi durumu eşitlenir (change `change-cancel-rejection-syncs-shipment`). Entegratör gönderi iptalini reddedince iptal geri alınır ve gönderinin durumu hemen okunur; paket kargoya verilmişse sipariş "Kargoda"ya geçer, alıcıya kargo e-postası gider ve yanıt 409 `ORDER_ALREADY_SHIPPED` olur. İptal penceresi mesajı gösterir, sayfa pencere kapanınca yenilenir. Kararlar ADR-105, ADR-106; ADR-076'ya not. L36 kapandı.
- 2026-10-19: `add-delivered-email` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-19-add-delivered-email/`). Ön koşullar: delta spec `shipments` ana spec'ine sync edildi; D1–D2 → ADR-091, ADR-092; dokümanlar güncel; `api` 323 ve `web` 275 test yeşil.
- 2026-10-19: "Teslim edildi" e-postası (change `add-delivered-email`). Satıcı siparişi `DELIVERED`'a geçince alıcıya, hesabının dilinde: üretici, sorun bildirimi için son an (Türkiye saatiyle tarih-saat ve 48 saat) ve sipariş bağlantısı. Yinelenen bildirim ikinci e-posta üretmez. Tarih-saat biçimi `common/time/DateTimeFormat`'ta. Kararlar ADR-091, ADR-092; ADR-078'e not. Bilinçli sınırlardaki eskimiş "ayıplı ürün talebi yok" maddesi düzeltildi. L41 kapandı.
- 2026-10-14: Teslim zamanı (`shipments.delivered_at`) artık ayıplı ürün talebinin 48 saatlik penceresini başlatır. Ayrıntı: [Ayıplı Ürün ve İade](ayipli-urun-ve-iade.md) (change `add-defect-claims`, ADR-081).
- 2026-10-10: `add-shipments` change'i kullanıcı onayıyla archive edildi (`openspec/changes/archive/2026-10-10-add-shipments/`). Ön koşullar: delta spec'ler ana spec'lere sync edildi (`shipments` yeni, `orders` değişti); D1–D8 → ADR-071…078; dokümanlar güncel; `api` 271 ve `web` 226 test yeşil; PR #5. Taşımada göreli linkler bir seviye uzatıldı. Açık ve notlu: etiket saklama süresi ve iadesiz kapatmanın hukuki uygunluğu (KALANLAR A5), gerçek entegratör (KALANLAR A2).
- 2026-10-10: Change `add-shipments` uygulandı.
  - `api` (`shipping` paketi): `ShippingIntegrator` + `FakeShippingIntegrator`; `POST /api/v1/sellers/me/orders/{id}/shipment`, `GET …/shipment/label`; zamanlanmış durum sorgusu; satıcı siparişi `SHIPPED`, `DELIVERED`, `DELIVERY_FAILED`, `REFUNDED`, `CLOSED`; iptal önce gönderiyi iptal eder; admin `GET/POST /api/v1/admin/seller-orders…`; üç e-posta.
  - Şema: `013-shipments.sql`.
  - `web`: satıcı sipariş detayında "Kargoya hazırla" ve gönderi kartı (etiket, gönderi kodu), alıcı detayında gönderi, `/admin/orders`; BFF `/api/seller/orders/[id]/shipment`, `…/label`, `/api/admin/seller-orders/[id]/resolution`.
  - Ortaklaştırılan: korumalı dosya başlıkları `ProtectedFileResponses` (satıcı belgesi de kullanır).
  - Testler: 271 `api` (+34), 226 `web` (+32).
  - Kararlar: ADR-071…078.
- 2026-10-10: Change `add-shipments` önerildi ve onaylandı; uygulama başladı.

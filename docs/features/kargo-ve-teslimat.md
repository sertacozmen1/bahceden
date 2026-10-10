# Kargo ve Teslimat

**Durum:** Geliştirmede
**İlk sürüm:** -
**Son değişiklik:** 2026-10-10

| | |
|---|---|
| **Ürün belgesi** | [Kargo ve Teslimat](../product/kargo-ve-teslimat.md) |
| **Kararlar** | Çerçeve: [ADR-057](../adr/057-shipping-via-integrator-port-integrator-pending.md), [ADR-058](../adr/058-flat-shipping-fee-per-seller-and-declared-handover.md), [ADR-056](../adr/056-cancel-before-shipment-and-defect-refund.md). Uygulama: [ADR-071](../adr/071-shipping-package-integrator-port-fake-only.md) … [ADR-078](../adr/078-shipping-notifications-email-only-three-events.md) |
| **Spec (capability)** | `openspec/specs/shipments` (yeni), `openspec/specs/orders` |
| **Change'ler** | `openspec/changes/add-shipments` (açık) |
| **Rehberler** | [Yerel geliştirme](../how-to/yerel-gelistirme.md) |
| **Referans** | [Domain modeli](../reference/01-domain-model.md), [API referansı](../reference/02-api-reference.md) |
| **Kod** | `api/src/main/java/com/bahceden/shipping/` (`ShipmentService`, `ShipmentStatusPoller`, `FakeShippingIntegrator`), `api/src/main/java/com/bahceden/order/` (`ShipmentProgressService`, `UndeliveredOrderService`), `web/src/features/orders/components` (`PrepareShipment`, `ShipmentCard`), `web/src/app/(admin)/admin/orders` |

---

## Ne yapar

Üretici ödenmiş siparişi hazırladığında satıcı panelinden koli boyutunu seçip gönderi açar; sistem kargo entegratöründen etiketi ve gönderi kodunu alır. Üretici etiketi yazdırır ya da kodu şubede söyler ve paketi şubeye bırakır. Gönderinin durumu kendiliğinden güncellenir: alıcı kargoya verildiğinde e-posta alır, sipariş sayfasında firmayı, takip numarasını ve durumu görür. Teslim edilemeyen paketlerde admin tam iade ya da gerekçeyle kapatma kararı verir.

## Bilinçli sınırlar

- **Kargo entegratörü sahte** (`fake`); gerçek entegratör seçilmedi (KALANLAR A2). Canlıda gönderi açılamaz.
- **Ayıplı ürün talebi ve kısmi iade yok** (sonraki change; ADR-056 §4–5).
- **Kapıdan alım ve çoklu koli yok**; paket şubeye teslim edilir.
- **SMS yok**; bildirimler e-posta (KALANLAR B4).

## Açık konular

- Etiketlerin saklama süresi (KALANLAR A5).
- Gerçek entegratörün durum bildirme biçimi (webhook / sorgu) ve şubede kodla kabul (KALANLAR A2).

## Tarihçe

- 2026-10-10: Change `add-shipments` uygulandı (archive bekliyor).
  - `api` (`shipping` paketi): `ShippingIntegrator` + `FakeShippingIntegrator`; `POST /api/v1/sellers/me/orders/{id}/shipment`, `GET …/shipment/label`; zamanlanmış durum sorgusu; satıcı siparişi `SHIPPED`, `DELIVERED`, `DELIVERY_FAILED`, `REFUNDED`, `CLOSED`; iptal önce gönderiyi iptal eder; admin `GET/POST /api/v1/admin/seller-orders…`; üç e-posta.
  - Şema: `013-shipments.sql`.
  - `web`: satıcı sipariş detayında "Kargoya hazırla" ve gönderi kartı (etiket, gönderi kodu), alıcı detayında gönderi, `/admin/orders`; BFF `/api/seller/orders/[id]/shipment`, `…/label`, `/api/admin/seller-orders/[id]/resolution`.
  - Ortaklaştırılan: korumalı dosya başlıkları `ProtectedFileResponses` (satıcı belgesi de kullanır).
  - Testler: 271 `api` (+34), 226 `web` (+32).
  - Kararlar: ADR-071…078.
- 2026-10-10: Change `add-shipments` önerildi ve onaylandı; uygulama başladı.

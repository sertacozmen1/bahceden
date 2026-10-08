# [ADR-067] Kargodan önce iptal: alıcı her zaman, satıcı gerekçeyle; satıcı siparişinin tamamı iade, stok geri eklenir

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D9

---

## Context

[ADR-056](056-cancel-before-shipment-and-defect-refund.md) §1–2 kargoya verilmeden önce iptali tam iade olarak tanımlıyor; satıcı da gerekçeyle iptal edebilir. İptal satıcı siparişinin birimidir. Para ile kayıt ayrışmamalı.

## Decision

- `NEW` durumundaki satıcı siparişi iki uçtan iptal edilir:
  - alıcı: `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/cancel`, gerekçe isteğe bağlı; süreye bağlı değildir (süre aşımı yalnızca bilgi);
  - satıcı: `POST /api/v1/sellers/me/orders/{id}/cancel`, gerekçe zorunlu (10–500 karakter), onaylı satıcı kapısından geçer.
- **Tek transaction:** satıcı siparişi kilitlenir; ürünler kimlik sırasıyla kilitlenip stoğa geri eklenir; satıcı siparişi `CANCELLED` olur, iptal eden ve gerekçe yazılır; geçmiş kaydı; sağlayıcıdan satıcı siparişinin ürün tutarı + kargo ücreti, satıcının alt üye payından iade edilir; `refunds` satırı (`CANCELLED`). Komisyon ve hakediş tutarları denetim izi için korunur; geçersizlik durumdan okunur.
- İade başarısızsa transaction geri alınır ve 502 `PAYMENT_PROVIDER_ERROR` döner: hiçbir şey değişmez.
- İptal edilmiş veya ödenmemiş satıcı siparişi 409 `ORDER_INVALID_TRANSITION`. Siparişin diğer satıcı siparişleri etkilenmez.
- Karşı tarafa hesabının dilinde e-posta gider (alıcı iptalinde satıcıya, satıcı iptalinde gerekçe ve iade tutarıyla alıcıya); gönderilemezse iptal yine geçerlidir.
- `web`'de iptal ortak gerekçe diyaloğuyla yapılır; diyalog alıcı için gerekçeyi isteğe bağlı kılan bir seçenek kazandı.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Satır bazında iptal | Kısmi iade ve komisyonun yeniden hesabı ayıplı ürün akışının konusu. |
| Önce iptal, sonra asenkron iade | Para ile kayıt arasında tutarsızlık penceresi; kuyruk gerekir. |

## Consequences

### Olumlu
- Alıcı ve satıcı aynı kurala tabidir; para, stok ve durum birlikte değişir.

### Olumsuz
- İade, sağlayıcıya eşzamanlı çağrıdır; sağlayıcı yavaşsa iptal isteği bekler.
- Commit'ten önce sağlayıcı iadeyi yapıp commit başarısız olursa iade kaydı oluşmaz (nadir; gerçek sağlayıcıyla mutabakat gerekir).

### Nötr / Trade-off
- Kargoya verildikten sonraki iade (ayıplı ürün) ayrı change'te gelir.

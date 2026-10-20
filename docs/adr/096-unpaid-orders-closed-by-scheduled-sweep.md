# [ADR-096] Ödeme dönüşü gelmeyen siparişler zamanlanmış işle, dönüşle aynı kuralla kapatılır

**Tarih:** 2026-10-20
**Durum:** Accepted
**Kaynak:** `add-stale-record-cleanup` design.md D1

---

## Context

Sipariş `PENDING_PAYMENT` açılır ve ödeme 30 dakikada tamamlanmalıdır. Ödeme dönüşü sonucu sağlayıcıdan doğrular, süresi geçmiş siparişi kapatır ve geç alınmış ödemeyi iade eder ([ADR-066](066-payment-redirect-verify-lock-stock-on-success.md)). Alıcı dönmezse bu kontrol hiç çalışmaz: sipariş süresiz "ödeme bekleniyor" kalır (`00-current-state.md` L32) ve sağlayıcıda geç tamamlanmış bir ödeme iade edilmez.

Siparişler silinmez (`AGENTS.md` §11.4). Zamanlanmış iş deseni gönderi sorgusunda ve talep süre işinde kurulu ([ADR-075](075-shipment-status-by-scheduled-polling.md), [ADR-083](083-defect-claim-deadlines-by-scheduled-sweep.md)).

## Decision

- İş (`PendingPaymentSweep`) her `bahceden.cleanup.interval`'da (10 dakika; dev'de 1) çalışır. Ödeme süresi `bahceden.cleanup.pending-payment-grace` (1 saat) kadar önce dolmuş `PENDING_PAYMENT` siparişleri en eskiden başlayarak alır; turda en çok `pending-payment-batch-size` (100).
- Her sipariş kendi transaction'ında `PESSIMISTIC_WRITE` ile kilitlenir ve yeniden okunur. Hâlâ ödeme bekliyorsa ödeme dönüşünün kullandığı **aynı** kod yolu çalışır (`CheckoutService.expireUnpaid`):

  | Sağlayıcı | Sonuç |
  |---|---|
  | Başarılı | Tutarın tamamı `PAYMENT_EXPIRED` nedeniyle iade; sipariş `PAYMENT_EXPIRED` |
  | Başarısız | `PAYMENT_FAILED` |
  | Bekliyor | `PAYMENT_EXPIRED` |

  Satıcı siparişleri `VOID` olur. Geçmişe yapan taraf sistemdir.
- Sağlayıcı hatası o siparişin transaction'ını geri alır; sipariş sonraki turda yeniden denenir, diğerleri işlenir.
- Stok ve sepet değişmez; e-posta gitmez.
- Arama için yalnızca ödeme bekleyen satırları kapsayan kısmi indeks eklenir: `orders (payment_expires_at, id) where status = 'PENDING_PAYMENT'` (`016`).

| Alternatif | Neden reddedildi |
|---|---|
| Ödenmemiş siparişleri silmek | Mali kayıt ve sağlayıcı kaydıyla eşleşme kaybolur. |
| Sağlayıcıya sormadan `PAYMENT_EXPIRED` yazmak | Geç tamamlanmış ödeme iadesiz kalır. |
| Listelerde gizlemek | Kayıt değişmez; sorun ekrana taşınır. |
| Sağlayıcı bildirimi (webhook) | Gerçek sağlayıcı seçimine bağlı (KALANLAR A1). |
| Tolerans olmadan süre dolar dolmaz kapatmak | Alıcı dönerken iş ile yarışır; dönüşün öncelikli olması tercih edildi. |

## Consequences

### Olumlu
- Alıcının listesinde süresiz "ödeme bekleniyor" kalmaz.
- Geç alınmış ödeme, alıcı dönmese de iade edilir.
- Dönüş ve iş aynı kuralı paylaşır; kilit çift kapatmayı ve çift iadeyi önler.

### Olumsuz
- Sağlayıcıda ödeme toleranstan da sonra tamamlanırsa sipariş kapanmış olur ve iş iade yapmaz. Tolerans gerçek sağlayıcının oturum süresine göre ayarlanmalı (A1).
- İş her örnekte çalışır (tek örnek varsayımı, L33 ve L38 ile aynı sınır).

### Nötr / Trade-off
- Kapatılan sipariş için e-posta gitmez; alıcı listesinde durumu görür.

# [ADR-061] İlanda kargoya verme süresi; son tarih iş günüyle hesaplanır, yalnızca hafta sonu dışlanır

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D3

---

## Context

[ADR-058](058-flat-shipping-fee-per-seller-and-declared-handover.md) satıcının her ilanda kargoya verme süresini (1–3 iş günü) beyan etmesini istiyor ve mevcut ilanlar için bir varsayılan gerektiğini not ediyor. Süre aşılırsa alıcı bunu görmeli.

## Decision

- `products.dispatch_days integer not null default 2`, değer 1–3. Mevcut ilanlar 2 alır.
- `ListingRequest.dispatchDays` **isteğe bağlıdır**: gönderilmezse yeni ilanda 2 olur, güncellemede mevcut değer korunur. Sözleşmede `required` değişikliği yoktur.
- Değer satıcının ilan formunda, ürün detayında ve sepette görünür.
- Satıcı siparişinin süresi, içindeki ürünlerin **en uzun** süresidir ve siparişe yazılır.
- Son tarih (`dispatch_due_at`) ödeme anında hesaplanır: Türkiye saatiyle (`Europe/Istanbul`) ödeme gününden sonraki N. iş gününün 23:59:59'u; cumartesi ve pazar atlanır; UTC saklanır (`common/time/BusinessDays`). Örnek: cuma + 2 → salı 23:59:59.
- Resmi tatiller hesaba katılmaz. Süre aşımı ekranda bilgi olarak gösterilir; yaptırımı yoktur (alıcı zaten kargodan önce her zaman iptal edebilir, [ADR-056](056-cancel-before-shipment-and-defect-refund.md)).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Alanı istekte zorunlu yapmak | Sözleşmede `required` değişikliği (§9); gereksiz. |
| Resmi tatil listesini yapılandırmada tutmak | Her yıl elle bakım ister; unutulursa son tarih sessizce yanlış olur. |
| Takvim günü | Cumartesi verilen sipariş pazartesi kargolanabilir; satıcı haksız yere "süre aştı" görünür. |

## Consequences

### Olumlu
- Alıcı ürünün ne zaman çıkacağını ürün sayfasında ve siparişte görür.
- Kırıcı sözleşme değişikliği yok.

### Olumsuz
- Bayramlarda satıcı süre aşmış görünebilir (yalnızca bilgi düzeyinde).

### Nötr / Trade-off
- Kolon `integer`'dır: Hibernate `int` alanı `smallint` ile doğrulamaz; değer aralığı `check` kısıtıyla sabittir.

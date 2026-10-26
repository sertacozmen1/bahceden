# [ADR-106] İptal penceresi "kargoya verildi" mesajını gösterir; sayfa pencere kapanınca yenilenir

**Tarih:** 2026-10-26
**Durum:** Accepted
**Kaynak:** `change-cancel-rejection-syncs-shipment` design.md D2

---

## Context

[ADR-105](105-cancel-refusal-reads-shipment-status-409-when-shipped.md) ile iptal, paket kargoya verilmişse 409 `ORDER_ALREADY_SHIPPED` döner ve siparişi "Kargoda"ya geçirir. İptal, gerekçe isteyen ortak pencereyle (`ReasonDialog`) yapılır. Pencere sunucu hatasını formun üstünde gösterir; başarıda sayfa sunucudan yenilenir. Sayfa yenilenince sipariş artık iptal edilemez olur, iptal düğmesi kalkar ve onunla birlikte pencere de kaybolur.

## Decision

- `CancelSellerOrder`, `ORDER_ALREADY_SHIPPED` hatasını pencerede gösterir ve sayfanın eskidiğini not eder.
- `ReasonDialog`'a isteğe bağlı `onClose` eklenir: kullanıcı pencereyi gönderim olmadan kapattığında çağrılır. `CancelSellerOrder` bu anda, not varsa, sayfayı `router.refresh()` ile yeniler.
- Diğer hatalarda ve başarıda davranış değişmez. Yeni metin yoktur: mesaj `api`'den isteğin dilinde gelir ([ADR-031](031-api-messages-by-accept-language.md)).

| Alternatif | Neden reddedildi |
|---|---|
| Hatada hemen yenilemek | Düğme ve pencere kalkar; kullanıcı mesajı okuyamadan kaybeder. |
| Sayfa düzeyinde ayrı bir bildirim alanı | Yeni durum yönetimi gerekir; pencere mesajı zaten gösteriyor. |

## Consequences

### Olumlu
- Kullanıcı nedeni okur, pencereyi kapatınca güncel durumu görür.

### Olumsuz
- Kullanıcı pencereyi kapatmadan sekmeyi bırakırsa sayfa eski durumu gösterir; bir sonraki gezintide güncellenir.

### Nötr / Trade-off
- `ReasonDialog`'u kullanan diğer ekranlar `onClose` vermez; davranışları değişmez.

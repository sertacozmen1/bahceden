# [ADR-065] Fiyatlama tek sınıfta: kargo ücreti ve komisyon kuruş tamsayısıyla, `HALF_UP`; değerler yapılandırmada

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D7

---

## Context

[ADR-055](055-commission-percent-plus-fixed-from-seller.md) komisyonu, [ADR-058](058-flat-shipping-fee-per-seller-and-declared-handover.md) kargo ücretini tanımlıyor. `AGENTS.md` §11.3 para hesabının tek yardımcıda ve `critical` testle sabit olmasını, `double` kullanılmamasını istiyor. Sepet ve sipariş aynı tutarları göstermek zorunda.

## Decision

- `order/OrderPricing` durum tutmayan bir hesap sınıfıdır; sepet ve sipariş aynı sınıfı kullanır.
- **Kargo ücreti:** grubun ürün tutarı ≥ eşik ise 0, değilse sabit ücret; satın alınabilir satırı olmayan grup kargo doğurmaz.
- **Komisyon:** `min(ürün tutarı, (tutar × bp + 5000) / 10000 + sabit)`; tutar negatif olmadığı için tamsayı bölme `HALF_UP` ile aynıdır. **Hakediş** = ürün tutarı − komisyon (negatif olamaz).
- **Yapılandırma** `bahceden.commerce` (açılışta doğrulanır): `commission-rate-bp: 500`, `commission-fixed-minor: 300`, `shipping-fee-minor: 9900`, `free-shipping-threshold-minor: 100000`, `payment-timeout: PT30M`, `delivery-provinces`.
- Değerler sipariş anında satıcı siparişine yazılır; yapılandırma değişikliği geçmişi etkilemez.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `BigDecimal` ile yüzde | Doğru ama tek çarpma için gereksiz; yuvarlama kipi yine açıkça seçilmeli. |
| Oranı veritabanında tutmak | Değiştirme yüzeyi (admin ekranı) yok; yapılandırma ve sipariş kopyası yeterli. |

## Consequences

### Olumlu
- Para kuralı tek yerde ve `critical` testli (245,50 TL → komisyon 15,28 TL; yarım kuruş; üst sınır; büyük tutar).
- Ekrandaki toplam ile alınan para ayrışamaz.

### Olumsuz
- Oran değişikliği yeniden başlatma gerektirir.

### Nötr / Trade-off
- `long` taşması pratikte imkânsızdır: satır fiyatı ≤ 10.000.000 kuruş × 99 adet × 50 satır.

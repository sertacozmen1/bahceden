# [ADR-082] Ayıplı ürün iadesinde tutar birim fiyat × adet; tam iadede kargo ve bütün komisyon döner; kısmi iadede komisyon kalan tutar üzerinden yeniden hesaplanır; stok eklenmez

**Tarih:** 2026-10-13
**Durum:** Accepted
**Kaynak:** `add-defect-claims` design.md D4; ürün sahibinin 2026-10-13 kararı (adet bazlı kısmi iade)

---

## Context

[ADR-056](056-cancel-before-shipment-and-defect-refund.md) §5 kuralı koydu:
- iade edilen ürün tutarı satıcı hakedişinden düşer;
- komisyonun yüzde kısmı iade edilen tutar oranında geri hesaplanır, sabit ücret yalnızca tam iadede geri alınır;
- kargo ücreti yalnızca tam iadede döner.

Komisyon oranı ve sabit ücret sipariş anında satıcı siparişine yazılır ([ADR-055](055-commission-percent-plus-fixed-from-seller.md)). Fiyatlama tek sınıftadır ve kuruş tamsayısıyla, `HALF_UP` ile hesaplar ([ADR-065](065-order-pricing-integer-minor-units-half-up.md)).

## Decision

1. **Hesap tek yerde:** `OrderPricing`'e saf bir hesap eklenir: `afterRefund(itemsTotal, refunded, rateBp, fixed, full)`. Hesap satıcı siparişinin sipariş anındaki oranını ve sabit ücretini kullanır, bugünkü yapılandırmayı değil. Bu hesap `critical` testle sabitlenir.
2. **İade tutarı:** her satırda birim fiyat × iade adedinin toplamıdır.
3. **Tam ve kısmi iade:**

   | Durum | Alıcıya iade | Komisyon | Hakediş |
   |---|---|---|---|
   | **Tam iade** (her satırın satın alınan bütün adetleri) | ürün tutarı + kargo ücreti | 0 | 0 |
   | **Kısmi iade** | iade tutarı | `HALF_UP((itemsTotal − iade) × bp / 10000) + sabit`, kalan tutarı aşamaz | `itemsTotal − iade − komisyon` |

4. **Kayıt:** iade tutarı, kargonun iade edilip edilmediği, yeni komisyon ve yeni hakediş talebe yazılır.
5. **İadenin yapılması:** `PaymentService.refund` çağrılır; neden yeni `RefundReason.DEFECT` değeridir (`refunds_reason_check` genişler) ve iade satıcının alt üye payından yapılır.
   - İade ile talebin `REFUNDED` olması aynı transaction'dadır.
   - Sağlayıcı hatasında hiçbir şey değişmez: kullanıcı isteğinde 502 döner, süre işinde sonraki çalışmada yeniden denenir.
6. **Stok** geri eklenmez: ürün geri gelmez, dönen taze ürün de satılabilir sayılmaz ([ADR-077](077-undelivered-order-admin-refund-or-close.md) ile aynı ilke).

**Örnek:** ürün tutarı 200 TL, oran %5, sabit 3 TL. 50 TL kısmi iadede yeni komisyon 150 × %5 + 3 = 10,50 TL, yeni hakediş 139,50 TL olur.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Komisyonu iade oranında düşürmek (`komisyon × kalan / toplam`) | Sabit ücreti de orantılar; ADR-056 §5 sabit ücretin yalnızca tam iadede döneceğini söyler. |
| Kısmi iadede de kargo ücretini iade etmek | ADR-056 §5'e aykırı. |
| Komisyonu bugünkü yapılandırmayla yeniden hesaplamak | Sipariş anının değeri değişmez ilkesine aykırı (ADR-055). |
| İadede stoğu artırmak | Ürün satıcıya geri dönmez. |

## Consequences

### Olumlu
- Satıcı her kısmi iadenin hakedişine etkisini önceden görebilir (önizleme ucu, [ADR-086](086-defect-claim-web-surfaces.md)).
- Para kuralı tek sınıfta ve testle sabittir.

### Olumsuz
- **Açık soru:** gerçek bölünmüş ödeme sağlayıcısında kısmi iadenin satıcı payı ile pazaryeri payı (geri dönen komisyon) arasındaki bölüşümü sağlayıcının API'sine bağlıdır. `PaymentProvider.refund` bugün yalnızca alt üye anahtarı ve tutar alır; sağlayıcı seçilince (KALANLAR A1) gerekirse genişletilir. `commission_after` ve `payout_after` bölüşümün kaynağıdır.
- İade tek sağlayıcı çağrısıdır; commit öncesi iade yapılıp commit düşerse kayıt oluşmaz ([ADR-067](067-cancel-before-shipment-full-refund-restock.md) ile aynı olumsuz sonuç).

### Nötr / Trade-off
- Kısmi iadeden sonra fatura veya e-arşiv düzeltmesi bu kararın kapsamında değil.

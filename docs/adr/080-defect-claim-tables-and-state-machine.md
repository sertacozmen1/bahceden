# [ADR-080] Talep tabloları ve talep durum makinesi: açık, teklif, ret, yönetimde, iade edildi, kapandı

**Tarih:** 2026-10-13
**Durum:** Accepted
**Kaynak:** `add-defect-claims` design.md D2; ürün sahibinin 2026-10-13 kararları (adet bazlı kısmi iade, 48 saatlik süreler, alıcının sessizliği kabul sayılır)

---

## Context

[ADR-056](056-cancel-before-shipment-and-defect-refund.md) §4 tarafların hamlelerini sayar: satıcı kabul eder, kısmi iade önerir ya da reddeder; alıcı kabul etmezse veya satıcı süresinde yanıt vermezse admin karar verir. Ürün sahibi kısmi iadeyi adet bazında, her iki tarafın yanıt süresini 48 saat olarak belirledi.

Sipariş ve ilan geçişleri her projede tek bir geçiş enum'unda tutuluyor (`SellerOrderTransition`, `ProductTransition`). Geçmiş kayıtları yalnızca eklenir.

## Decision

1. **Tablolar** (`014-defect-claims.sql`):

   | Tablo | İçerik |
   |---|---|
   | `defect_claims` | `seller_order_id` (benzersiz), `status`, `description` (10–1000), `seller_reason`, `seller_deadline_at`, `buyer_deadline_at`, `refund_amount`, `shipping_refunded`, `commission_after`, `payout_after`, `resolved_by` (`SELLER`, `BUYER`, `ADMIN`, `DEADLINE`), `resolved_at`, zaman damgaları |
   | `defect_claim_lines` | `claim_id`, `order_item_id` (talep içinde benzersiz), `claimed_quantity`, `offered_quantity`, `refunded_quantity` |
   | `defect_claim_photos` | `claim_id`, `position` (0–2), `card_key`, `large_key`, boyutlar |
   | `defect_claim_changes` | yalnızca eklenir: önceki ve yeni durum, eylemci hesap (boşsa süre işi), gerekçe, zaman |

2. **Durumlar** (`DefectClaimStatus`; geçişler `DefectClaimTransition`'da tek yerde):

   | Durum | Anlamı | Çıkışlar |
   |---|---|---|
   | `OPEN` | Satıcının yanıtı bekleniyor | satıcı kabul → `REFUNDED`; satıcı teklif → `OFFERED`; satıcı ret → `REJECTED`; süre → `ESCALATED` |
   | `OFFERED` | Satıcı daha az adet önerdi; alıcının yanıtı bekleniyor | alıcı kabul veya süre → `REFUNDED`; alıcı taşır → `ESCALATED` |
   | `REJECTED` | Satıcı reddetti; alıcının yanıtı bekleniyor | alıcı kabul veya süre → `CLOSED`; alıcı taşır → `ESCALATED` |
   | `ESCALATED` | Admin kararı bekleniyor | admin iade → `REFUNDED`; admin ret → `CLOSED` |
   | `REFUNDED` | İade yapıldı (tam veya kısmi); uç durum | — |
   | `CLOSED` | İadesiz kapandı; uç durum | — |

3. **Adet kuralları:**
   - Satıcının teklifi en az bir adet içerir ve her satırda alıcının istediği adeti aşmaz.
   - Bütün satırlarda istenen adeti önermek, teklif değil kabuldür.
   - Hiç adet önermemek retle aynıdır ve gerekçe ister.
   - Admin her satırda satın alınan adete kadar iade edebilir. En az bir adet iade etmeyen admin kararı rettir ve gerekçe ister.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Satıcının serbest tutar önermesi | Ürün sahibi adet bazını seçti: hesap kesin, pazarlık az. |
| Durumu saklamayıp sürelerden türetmek | Süre işi ve admin listesi indeksli bir durum kolonuna dayanır; para hareketi okuma anında yapılamaz. |
| Alıcının teklife karşı teklif vermesi | Görüşmeyi uzatır; anlaşmazlığın çözüm yeri admin hakemliğidir. |
| Admin'in en fazla alıcının istediği kadar iade edebilmesi | Alıcı ilk talepte eksik adet yazmış olabilir ve ikinci talep yoktur (ADR-079); admin hakemliği bunu düzeltebilmeli. |

## Consequences

### Olumlu
- Her talep en fazla iki tur sonra uç duruma ulaşır: satıcı yanıtı, ardından alıcı yanıtı ya da admin kararı.
- Geçmiş tablosu kimin, ne zaman, hangi gerekçeyle ne yaptığını silinmeden tutar.

### Olumsuz
- Üç ayrı adet kolonu (istenen, önerilen, iade edilen) ekranda dikkatli gösterilmelidir.
- Sessizliğin sonuç doğurması (süre işi) iki taraf için de açıkça yazılmalıdır; e-posta son tarihi söyler.

### Nötr / Trade-off
- `resolved_by` raporlama içindir; kararı veren hesap geçmiş tablosundadır.

# [ADR-010] Ürün yayın durumu enum'dur, boolean bayrak değil

**Tarih:** 2026-10-06
**Durum:** Accepted
**Kaynak:** `add-storefront-product-listing` change, `design.md` D1

---

## Context

Vitrin yalnızca yayınlanmış ürünleri göstermek zorunda. En kısa yol `products.is_active boolean` bir bayrak koymaktı.

Ancak bilinen iki ihtiyaç bu bayrağa sığmıyor: satıcı ilanı hazırlayıp sonra yayınlayacak (taslak), sezonu kapanan ürün listeden çıkacak ama geçmiş siparişlerde kalacak (arşiv). Satıcı onboarding kararı (`KALANLAR.md` A3) ilan öncesi onay getirirse dördüncü bir durum daha gelecek.

## Decision

`products.status` alanı bir enum'dur: `DRAFT | PUBLISHED | ARCHIVED`. Veritabanında metin + `CHECK` kısıtı, Java'da `@Enumerated(EnumType.STRING)`.

Yayınlanmış bir ürünün yayın tarihi olmak zorundadır; bu bir veritabanı kısıtıyla güvence altındadır (`status <> 'PUBLISHED' or published_at is not null`): vitrin sıralaması bu alana dayanıyor ve geçersiz durum veritabanında temsil edilemez.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `is_active boolean` | Üçüncü durum geldiğinde şema göçü ve her sorguda ikinci bir koşul gerekir. |
| İki boolean bayrak (`is_active`, `is_archived`) | Geçersiz kombinasyonlar (aktif **ve** arşiv) temsil edilebilir hale gelir; "mümkün olmayan durum" kuralı koda taşınır ve unutulur. |
| Veritabanında native `enum` tipi | PostgreSQL enum'una değer eklemek migration gerektirir ve sıralaması gizli bir sözleşmedir; metin + `CHECK` daha okunabilir ve değiştirilebilir. |
| Durumu ayrı bir tabloda tutmak (durum geçmişi) | Bugün geçmişe ihtiyaç yok; ihtiyaç doğarsa ayrı karar. |

## Consequences

### Olumlu
- Yeni durum eklemek tek bir `CHECK` kısıtı ve enum değeri demek; sorgular değişmez.
- Geçersiz durum veritabanı seviyesinde engelli.
- Vitrin sorgusu okunabilir: `status = 'PUBLISHED'`.

### Olumsuz
- Enum değeri eklemek migration gerektirir (boolean ile `true/false` bedava gelirdi).
- Enum adı sözleşmeye sızar: istemci `"PUBLISHED"` sabitini bilir, dolayısıyla yeniden adlandırma kırıcı değişikliktir.

### Nötr / Trade-off
- Durum geçişlerinin kuralı (kim neyi ne zaman yayınlayabilir) bu ADR'nin kapsamı dışında; satıcı ürün yönetimi dilimiyle gelecek.

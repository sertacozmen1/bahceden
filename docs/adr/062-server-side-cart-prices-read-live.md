# [ADR-062] Sepet sunucuda, hesaba bağlı tek kayıt; fiyat ve stok her okumada üründen gelir

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D4; ürün sahibinin 2026-10-08 kararı (sepet sunucuda, oturum gerekli)

---

## Context

Alıcı birden çok üreticinin ürününü tek sepette toplamalı ve kargo ücretini önceden görmeli. Ürün sahibi misafir sepeti istemedi.

## Decision

- Tablolar `carts` (`account_id` benzersiz) ve `cart_items` (`cart_id`, `product_id`, `quantity`; `(cart_id, product_id)` benzersiz).
- **Sepette fiyat tutulmaz.** `GET /api/v1/cart` her satırın güncel fiyatını, stoğunu ve satın alınabilirliğini ([ADR-059](059-payment-and-order-packages-single-purchasability-rule.md)) okur; satırları satıcıya göre gruplar; her grup için ara toplam, kargo ücreti, ücretsiz kargoya kalan tutar ve kargoya verme süresini, sepet için genel toplamı ve başlıktaki adet sayısını sunucu hesaplar.
- `PUT /api/v1/cart/items/{productId}` satırı ekler veya adedini değiştirir: adet 1–99 ve stoğu aşamaz; satın alınamayan ürün (422 `PRODUCT_NOT_PURCHASABLE`) ve satıcının kendi ürünü (422 `CANNOT_BUY_OWN_PRODUCT`) eklenemez; en çok 50 satır (422 `CART_FULL`). `DELETE` satırı çıkarır.
- Satın alınamaz hale gelen veya adedi güncel stoğu aşan satır sepette kalır, `available = false` işaretlenir ve toplamlara girmez.
- Sepet okuması ürünü ve satıcıyı tek sorguda getirir; kapaklar tek ek sorguyla gelir (≤ 3 sorgu, testle sabit).
- `web` başlıktaki sepet sayısını sunucuda oturumla birlikte okur; değişiklikten sonra sayfa yenilenir. İstemci tarafında sepet durumu tutulmaz.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Eklerken fiyatı sabitlemek | İki fiyat kaynağı; satıcı fiyat değiştirince bir taraf zarar eder. |
| Misafir sepeti (çerez) | Fiyat ve stok yine sunucudan okunmak zorunda; birleştirme kuralı gerekir; ürün sahibi istemedi. |
| İstemci tarafı sepet deposu | İki doğruluk kaynağı; state kütüphanesi K7 kararı ister. |

## Consequences

### Olumlu
- Alıcı her zaman güncel fiyatı görür; sepet cihazlar arasında korunur.
- Ekrandaki toplam ile alınan para aynı hesaptan gelir ([ADR-065](065-order-pricing-integer-minor-units-half-up.md)).

### Olumsuz
- Sepete eklemek için giriş gerekir; ilk alışverişte sürtünme.
- Başlıktaki sepet sayısı her sayfada bir ek `api` çağrısıdır (oturumlu kullanıcı için).

### Nötr / Trade-off
- Fiyat siparişte sabitlenir ([ADR-064](064-three-level-order-snapshot-idempotency-key.md)).

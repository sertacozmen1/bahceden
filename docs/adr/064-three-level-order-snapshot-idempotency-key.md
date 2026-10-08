# [ADR-064] Sipariş üç seviyede (sipariş, satıcı siparişi, satır); tutarlar ve adres sipariş anında yazılır; `Idempotency-Key` zorunlu

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D6

---

## Context

Bir sepet birden çok satıcı içerir; her satıcı ayrı paket gönderir, ayrı komisyon öder ([ADR-055](055-commission-percent-plus-fixed-from-seller.md)) ve ayrı iptal edilebilir ([ADR-056](056-cancel-before-shipment-and-defect-refund.md)). Fiyat ve yapılandırma sonradan değişebilir. Çift tıklama iki sipariş açmamalı (§11.5).

## Decision

- **`orders`:** iş anahtarı `order_number` (`BHC-` + 10 karakter Crockford base32, rastgele, benzersiz), `buyer_account_id`, durum, teslimat adresi kopyası (`ship_*`), ürün/kargo/genel toplam, para birimi, `idempotency_key` (hesapla birlikte benzersiz), `payment_expires_at`, `paid_at`.
- **`seller_orders`:** satıcı, durum, ürün tutarı, kargo ücreti, komisyon oranı (bp), sabit ücret, komisyon, hakediş, kargoya verme süresi ve son tarihi, iptal eden taraf ve gerekçe.
- **`order_items`:** ürün kimliği ve sipariş anındaki ad, adres, birim, birim fiyat, adet, satır tutarı.
- **`order_status_changes`:** satıcı siparişi seviyesinde, yalnızca eklenen geçmiş.
- **Durumlar:** sipariş `PENDING_PAYMENT`, `PAID`, `PAYMENT_FAILED`, `PAYMENT_EXPIRED`, `CANCELLED_OUT_OF_STOCK`; satıcı siparişi `PENDING_PAYMENT`, `NEW`, `CANCELLED`, `VOID`. Geçişler `SellerOrderTransition`'da tek yerde (`PAY`, `VOID`, `CANCEL`). Kargo durumları gönderi change'inde eklenir.
- **Oluşturma (`POST /api/v1/orders`, `{addressId}`, `Idempotency-Key` başlığı 8–64 karakter zorunlu):** sepet boşsa 422 `CART_EMPTY`; satın alınamayan veya stoğu aşan satır varsa 422 `CART_HAS_UNAVAILABLE_ITEMS`; satırlar satıcıya bölünür, tutarlar hesaplanır, adres kopyalanır, sipariş `PENDING_PAYMENT` yazılır, ödeme başlatılır ([ADR-066](066-payment-redirect-verify-lock-stock-on-success.md)). Stok bu adımda düşmez. Aynı anahtarla gelen istek ilk siparişi ve ödeme adresini döner. Hız sınırı: hesap başına saatte 20.
- **Görüntüleme:** alıcı yalnızca kendi siparişlerini görür (komisyon ve hakediş yok); satıcı yalnızca kendi `NEW`/`CANCELLED` satıcı siparişlerini görür ve alıcının adresinden yalnızca il ve ilçeyi alır. Başkasının kaydı 404.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Satıcı başına ayrı sipariş ve ödeme | Alıcı her satıcı için ayrı kart işlemi yapar; bölünmüş ödeme tek işlemde çözüyor. |
| Tek seviyeli sipariş | İptal, komisyon ve kargo için satıcı ayrımı her sorguda yeniden hesaplanır. |
| Sıralı sipariş numarası | Sipariş hacmini sızdırır ve tahmin edilebilir. |
| `Idempotency-Key` isteğe bağlı | Çift tıklama ve ağ tekrarı iki sipariş açabilir. |

## Consequences

### Olumlu
- Sonradan değişen fiyat, adres veya yapılandırma geçmiş siparişi etkilemez; denetim izi tamdır.
- Satıcıya alıcının tam adresi gösterilmez (K10).

### Olumsuz
- Aynı anahtarla tam eşzamanlı iki istek, benzersizlik kısıtında ikincisinin 500 almasıyla sonuçlanabilir (nadir; `web` anahtarı aynı ekranda korur).
- Ödemesi hiç tamamlanmayan siparişler `PENDING_PAYMENT` kalır; temizlik işi yoktur.

### Nötr / Trade-off
- Sipariş kayıtları silinmez (§11.4); saklama süresi A5'e bağlı.

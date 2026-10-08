# [ADR-066] Ödeme: başlat, yönlendir, tamamla; `fake` sağlayıcı onaylar; stok tamamlamada kilitle düşer, yetmezse ödemenin tamamı iade edilir

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D8; ürün sahibinin 2026-10-08 kararı (stok ödeme başarılı olunca düşer)

---

## Context

[ADR-054](054-split-payment-via-provider-port-provider-pending.md) sağlayıcıyı bir arayüzün arkasına koydu; seçim açık (KALANLAR A1). Gerçek sağlayıcılar 3D Secure için alıcıyı yönlendirir ve dönüşte sonucu doğrulatır. Ürün sahibi stok rezervasyonu istemedi; stok ödeme başarılı olunca düşmeli.

## Decision

- **`PaymentProvider`:** `registerSubMerchant`, `initiate` (referans + yönlendirme adresi), `verify` (başarılı / başarısız / bekliyor), `refund` (tutar ve isteğe bağlı satıcı payı). İstek her satıcı payını (alt üye anahtarı, satıcının ödediği tutar, hakediş) taşır.
- **Akış:** `POST /orders` siparişi yazar ve ödemeyi başlatır (`payments` satırı `INITIATED`, yönlendirme adresi saklanır). `web` alıcıyı adrese gönderir; `fake` sağlayıcıda bu adres doğrudan `/checkout/return?order=…` sayfasıdır. Dönüş sayfası sunucuda `POST /orders/{orderNumber}/payment` çağırır; sonuç sağlayıcıdan okunur, istemciye güvenilmez.
- **Başarılı ödeme (tek transaction):** sipariş satırı kilitlenir; ürünler kimlik sırasıyla `PESSIMISTIC_WRITE` kilitlenir; stok yeterliyse düşer, sipariş `PAID`, satıcı siparişleri `NEW` olur, son tarih yazılır ([ADR-061](061-listing-dispatch-days-business-days-weekends-only.md)), satın alınan ürünler sepetten çıkar; e-postalar transaction sonrasında gider.
- **Stok yetmezse** ödemenin tamamı iade edilir (`OUT_OF_STOCK`), sipariş `CANCELLED_OUT_OF_STOCK`, sepet kalır.
- **Başarısız** ödemede sipariş `PAYMENT_FAILED`, satıcı siparişleri `VOID`. **Süre dolduysa** (30 dk; çağrı anında denetlenir, zamanlanmış iş yok) sipariş `PAYMENT_EXPIRED`; sağlayıcı yine de başarılı derse ödemenin tamamı iade edilir (`PAYMENT_EXPIRED`). **Bekliyorsa** sipariş değişmez.
- Tamamlama tekrar çağrılabilir; sonuçlanmış siparişte mevcut sonuç döner. Sağlayıcı hatası 502 ve transaction geri alınır.
- **`FakePaymentProvider`** (`bahceden.payment.provider=fake`, tek geçerli değer): ağ çağrısı yapmaz, her işlemi onaylar. Başarısızlık ve iade hatası dalları testlerde ayrı bir test uygulamasıyla sınanır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `web`'de `fake` için "başarılı / başarısız" ara ekranı | Üretim kodunda yalnızca geliştirme için var olan bir ekran (K6). |
| Yönlendirmesiz tek çağrıda ödeme | Gerçek sağlayıcı geldiğinde akış ve ekranlar değişir. |
| Stok rezervasyonu (sipariş anında düş, ödemezse bırak) | Ürün sahibi istemedi; zamanlanmış temizlik işi gerekir. |
| İyimser kilit (`@Version`) | Çakışmada tekrar deneme döngüsü; kısa satır kilidi bu ölçekte daha basit. |
| Yetmeyen satıcıyı kısmen iade etmek | Alıcı eksik sepet için ödemiş olur. |

## Consequences

### Olumlu
- Stok negatife düşmez; eşzamanlı iki ödemede biri kazanır, diğeri tam iade alır (testle sabit).
- Gerçek sağlayıcı eklendiğinde akış değişmez.

### Olumsuz
- Aynı son ürünü iki alıcı ödeyebilir; ikincisi "para çekildi, iade edildi" deneyimi yaşar. İade oranı izlenir.
- Alıcı dönüş sayfasına gelmezse sipariş bekler; sağlayıcı bildirimi (webhook) gerçek sağlayıcıyla gelir.
- `fake` her şeyi onayladığı için dev ortamında başarısız ödeme elle denenemez.

### Nötr / Trade-off
- Dönüş sayfası tamamlamayı sunucu tarafında, sayfa açılırken yapar; çağrı tekrarlanabilir olduğu için yenileme güvenlidir.

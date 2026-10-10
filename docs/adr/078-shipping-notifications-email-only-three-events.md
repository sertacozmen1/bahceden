# [ADR-078] Kargo bildirimleri yalnızca e-posta ve üç olayda: kargoya verildi, teslim edilemedi, admin kararı

**Tarih:** 2026-10-10
**Durum:** Accepted
**Kaynak:** `add-shipments` design.md D8

---

## Context

Sipariş e-postaları `OrderNotifier`'da, transaction sonrasında ve hesabın dilinde gönderilir ([ADR-032](032-account-locale-stored-and-refreshed-on-login.md)); gönderim hatası akışı kesmez. SMS ve kanal seçimi KALANLAR B4'te açıktır. Alıcı durumu sipariş sayfasından izleyebilir.

## Decision

- `OrderNotifier` genişler (yeni sınıf yok, K9):
  - **Kargoya verildi:** alıcıya; üretici, kargo firması, takip numarası ve sipariş bağlantısı.
  - **Teslim edilemedi:** alıcıya ve satıcıya; yönetimin inceleyip karar vereceği söylenir.
  - **Admin kararı:** alıcıya ve satıcıya; iade tutarı ve açıklama ya da kapatma gerekçesi ([ADR-077](077-undelivered-order-admin-refund-or-close.md)).
- "Yolda" ve "teslim edildi" için e-posta yoktur; "teslim edildi" e-postası ayıplı ürün change'inde 48 saat bilgisiyle eklenecek.
- Takip numarası e-postaya girer (alıcının kendi verisi) ama uygulama log'una girmez (K10). Metinler `messages.properties` ve `messages_en.properties`'te.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Her gönderi durumunda e-posta | Gürültü; alıcı sipariş sayfasından izler. |
| SMS | KALANLAR B4 açık; K7 sağlayıcı kararı gerekir. |
| Şimdiden "teslim edildi" e-postası | Ayıplı ürün süresi bilgisiyle birlikte anlamlı; o change'te eklenir. |

## Consequences

### Olumlu
- Alıcı paketin yola çıktığını ve takip numarasını kendiliğinden öğrenir.
- Teslim edilemeyen sipariş iki tarafa da duyurulur.

### Olumsuz
- E-postası doğrulanmamış alıcıya da gider (L11 ile aynı borç).

### Nötr / Trade-off
- Bildirim kanalı kararı (B4) verildiğinde bu üç olay aynı noktalardan SMS'e de bağlanabilir.

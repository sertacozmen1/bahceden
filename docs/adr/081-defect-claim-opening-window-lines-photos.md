# [ADR-081] Talep açma: teslimden itibaren 48 saatlik pencere, satır ve adet, açıklama, 1–3 fotoğraf; tek multipart istek

**Tarih:** 2026-10-13
**Durum:** Accepted
**Kaynak:** `add-defect-claims` design.md D3; ürün sahibinin 2026-10-13 kararı (1–3 fotoğraf)

---

## Context

[ADR-056](056-cancel-before-shipment-and-defect-refund.md) §4 talebin teslimden itibaren 48 saat içinde, en az bir fotoğrafla ve satır bazında açılmasını ister. Teslim anının tek kaynağı gönderidir (`shipments.delivered_at`, [ADR-072](072-shipments-table-and-seller-order-shipping-states.md)).

İlan fotoğrafları için bir işleme hattı zaten var: döndürme, iki boyut, konum bilgisinin atılması ([ADR-050](050-photo-processing-exif-orientation-two-sizes.md)). Talep açan kişi çoğunlukla telefondan fotoğraf çeker.

## Decision

1. **Uç:** `POST /api/v1/orders/{orderNumber}/seller-orders/{id}/claim`, `multipart/form-data`.
   - `claim` JSON parçası: `{lines: [{orderItemId, quantity}], description}`.
   - `photos` parçası: 1–3 dosya.
2. **Koşullar:**

   | Koşul | Sağlanmazsa |
   |---|---|
   | Sipariş alıcının olmalı (sorgu seviyesinde) | 404 |
   | Satıcı siparişi `DELIVERED` olmalı | 409 `CLAIM_NOT_ALLOWED` |
   | Teslimden itibaren `bahceden.claims.window` (`PT48H`) dolmamış olmalı | 422 `CLAIM_WINDOW_CLOSED` |
   | Satıcı siparişinin talebi olmamalı; benzersiz kısıt eşzamanlı ikinci isteği de reddeder | 409 `CLAIM_NOT_ALLOWED` |
   | Her satır o satıcı siparişine ait olmalı, adet 1 ile satın alınan adet arasında olmalı, aynı satır iki kez gelmemeli | alan hatası |
   | Açıklama 10–1000 karakter olmalı | alan hatası |
   | 1–3 fotoğraf olmalı; her biri ilan fotoğrafıyla aynı tür ve boyut sınırlarından ve `PhotoProcessor`'dan geçmeli | alan hatası (okunamayan fotoğraf dahil) |

3. **Dosya ve kayıt sırası:** talep tek transaction'dır. Satıcı siparişi kilitlenir, böylece eşzamanlı ikinci talep sırayla işlenip reddedilir. Fotoğraflar işlenip yazılır ve transaction commit'lenmezse silinir (`FileStorage.writeWithinTransaction`). İlan fotoğrafının deseni bu ortak yardımcıya taşındı (K9).
4. **Hız sınırı:** talep açma hesap başına saatte 10 istekle sınırlıdır.
5. **Satıcının süresi:** `seller_deadline_at = şimdi + bahceden.claims.seller-response` (`PT48H`). Satıcıya talep e-postası gider.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Fotoğrafı ayrı uçla sonradan yüklemek | Fotoğrafsız talep diye bir ara durum doğar; ADR-056 fotoğrafı talebin parçası sayıyor. |
| 1–5 fotoğraf (ilan sınırı) | Ürün sahibi 1–3'ü seçti; telefondan hızlı yüklenir, işleme süresi kısalır. |
| Pencereyi satıcı siparişinin güncellenme anından hesaplamak | Teslim anının tek kaynağı gönderidir. |

## Consequences

### Olumlu
- Talep tek ekranda ve tek istekte tamamlanır.
- Fotoğraflar ilanlarla aynı güvenlik hattından geçer.

### Olumsuz
- Üç fotoğrafın işlenmesi istek içindedir; yavaş bağlantıda istek uzun sürer (L26 ile aynı sınır).

### Nötr / Trade-off
- 48 saatlik pencere hafta sonunu dışlamaz; ADR-056'nın süresi takvim saatidir.

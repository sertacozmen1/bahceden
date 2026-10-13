# [ADR-086] `web`: talep formu ayrı sayfada, talep kartı sipariş detaylarında, admin ekranı ayrı; para etkisi sunucu önizlemesinden

**Tarih:** 2026-10-13
**Durum:** Accepted
**Kaynak:** `add-defect-claims` design.md D8

---

## Context

Talep çoğunlukla telefondan açılır: satır seçimi, adet, açıklama ve fotoğraf. Satıcı sahada telefonla çalışır ve her yanıtın hakedişine etkisini bilmelidir. Ancak `web` para hesaplamaz (`AGENTS.md` §12.1).

Sipariş detay sayfaları, adet seçici (`QuantityStepper`), gerekçe penceresi (`ReasonDialog`) ve admin sipariş ekranı zaten var.

## Decision

1. **Alıcı:**
   - `/account/orders/[number]` sayfasında, pencere açık ve talep yokken satıcı siparişi kartında "Sorun bildir" bağlantısı görünür.
   - Form `/account/orders/[number]/claim?sellerOrder={id}` sayfasındadır: her satır için onay kutusu ve adet seçici (`QuantityStepper`, K9), açıklama, 1–3 fotoğraf. Fotoğraf girdisi `accept="image/*"` taşır; telefonda kamera açılır.
   - Talep kartı satıcı siparişi kartının içindedir: durum rozeti, satırlar (istenen, önerilen ve iade edilen adet), küçük fotoğraflar, son tarih ve sonuç.
   - `OFFERED` ve `REJECTED` durumunda "Kabul et" (birincil) ve "Yönetime taşı" düğmeleri bulunur.
2. **Satıcı:**
   - `/seller/orders/[id]` sayfasında talep kartı yer alır.
   - `OPEN` durumunda üç eylem vardır: "Tamamını kabul et" (birincil), "Kısmi iade öner" (satır başına adet) ve "Reddet" (`ReasonDialog`, K9).
   - Para etkisi her eylemde yazılır: "Alıcıya ₺X iade edilir, hakedişiniz ₺Y olur."
3. **Para etkisi sunucudan:** tutarı sunucu hesaplar. Yan etkisiz bir önizleme ucu satırları alır, iade tutarını ve yeni hakedişi döner: `POST /api/v1/sellers/me/orders/{id}/claim/preview`.
4. **Admin:** `/admin/claims` sekmeli listedir (`ESCALATED` varsayılan; `OPEN`, `OFFERED`, `REJECTED`, `REFUNDED`, `CLOSED`). Detayda büyük fotoğraflar, iki tarafın beyanları, satır başına iade adedi ve gerekçeli ret bulunur.
5. **Feature kaydı:** "Ayıplı Ürün ve İade" yeni bir ürün seviyesi feature'dır (`docs/features/ayipli-urun-ve-iade.md`).
6. **Durumlar ve metinler:** dört ekran durumu mevcut `loading.tsx` ve `error.tsx` dosyalarıyla karşılanır. Rozetler anlamsal renk rollerini kullanır ve metin taşır. Metinler `tr` ve `en` dosyalarına birlikte eklenir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Talep formunu pencerede açmak | Fotoğraf seçimi ve birden çok satır telefonda küçük pencerede zor. |
| Para etkisini ekranda hesaplamak | §12.1: istemci hesaplamaz, gösterir. |
| Talep kartını ayrı bir "Taleplerim" sayfasında göstermek | Talep siparişin parçasıdır; ikinci gezinme yolu açar. |
| Talebi "Kargo ve Teslimat" feature'ına eklemek | Kullanıcı bunu ayrı bir iş olarak adlandırır: iade. |

## Consequences

### Olumlu
- Satıcı her yanıtın hakedişine etkisini yanıt vermeden görür.
- Alıcı formu telefonda tek sayfada doldurur.

### Olumsuz
- Önizleme ucu her teklif değişiminde bir istek atar; adet seçici değiştikçe istek gecikmeli gönderilir.

### Nötr / Trade-off
- Admin ekranı teslim edilemeyen sipariş ekranından (`/admin/orders`) ayrıdır; ikisi de admin gezinmesinde yer alır.

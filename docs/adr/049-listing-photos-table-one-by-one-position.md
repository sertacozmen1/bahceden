# [ADR-049] İlan fotoğrafları ayrı tabloda, tek tek yüklenir; sıra açık pozisyonla, kapak `position = 0`

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-product-listings` design.md D3

---

## Context

Satıcı sahada telefonla tek tek fotoğraf çeker. Bir ilanın birden fazla fotoğrafı olur ve kapak, vitrin kartında görünen fotoğraftır. Taslak ilan, bilgiler girildiği anda vardır; yani satıcı başvurusundaki yetim kayıt sorunu ([ADR-042](042-seller-application-single-multipart-request.md)) burada yoktur.

## Decision

- `product_photos` tablosundaki her satırda `product_id`, `position` (0–4), iki boyutun anahtarı ve büyük boyutun ölçüleri bulunur. `(product_id, position)` tekildir ve kısıt transaction sonunda denetlenir (`deferrable initially deferred`), böylece sıralama tek transaction'da yer değiştirebilir.
- Uçlar:
  - fotoğraf yükleme: tek dosya, multipart, sona eklenir;
  - silme: kalanlar 0..n-1 olarak sıkıştırılır;
  - sıralama: `{"photoIds": [...]}`; liste eksiksiz ve tekrarsız olmalıdır.
- Sınırlar:
  - ilan başına en çok 5 fotoğraf (`PRODUCT_PHOTO_LIMIT_REACHED`);
  - yayındaki veya kaldırılmış ilanın son fotoğrafı silinemez (`PRODUCT_PHOTO_REQUIRED`);
  - yükleme hız sınırı hesap başına saatte 60.
- Silinen fotoğrafın dosyaları transaction başarıyla bittikten sonra silinir. Transaction geri alınırsa yazılan dosyalar silinir.

| Alternatif | Neden reddedildi |
|---|---|
| İlan ve bütün fotoğrafların tek istekte gelmesi | 5 × 10 MB tek istekte gider; bir fotoğraf hatasında hepsi yeniden yüklenir. |
| Genel bir dosya tablosu | Herkese açık ve gizli dosyalar karışır ([ADR-045](045-seller-documents-table-content-sniffing-protected-serving.md)). |
| Ayrı `cover_photo_id` alanı | Pozisyonla çelişebilir; iki kaynaklı gerçek oluşur. |

## Consequences

### Olumlu
- Mobil bağlantıda tek fotoğraf hatası yalnızca o fotoğrafı etkiler.
- Kapak ve sıra tek bir alanda tutulur.

### Olumsuz
- Bir ilanı fotoğraflarıyla hazırlamak birden fazla istek gerektirir.

### Nötr / Trade-off
- Sıralama sürükle-bırak yerine öne / arkaya düğmeleriyle yapılır; klavye ve dokunmayla aynı şekilde erişilir.

# [ADR-045] Belge `seller_documents` tablosunda; tür içerikten tespit edilir; yalnızca sahibine ve admin'e, korumalı başlıklarla sunulur

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-seller-onboarding` design.md D6

---

## Context

[ADR-039](039-uploads-on-local-volume-served-by-api.md) dosyaların yerel bir volume'da durmasını ve `api` tarafından sunulmasını sabitledi. Başvuru belgeleri kişisel veri içerir ve gizlidir. Ürün fotoğrafları ise ileride herkese açık olacak.

Yüklenen dosya bir saldırı yüzeyidir: uzantısı `.pdf` olan bir HTML dosyası tarayıcıda betik çalıştırabilir.

## Decision

- **Tablo:** her başvuru ve yeniden gönderim `seller_documents` tablosuna yeni bir satır yazar: tür, sunucunun ürettiği anahtar, içerik türü, boyut. Güncel belge en son satırdır. Önceki belgeler silinmez.
- **Disk erişimi:** tek bir sınıftan geçer, `common/storage/FileStorage`. Yazma önce geçici dosyaya yapılır, sonra dosya yerine taşınır. Anahtar biçimi okuma ve silmede de doğrulanır. Kök dizin açılışta oluşturulur ve yazılabilirliği denetlenir; dizin yazılamıyorsa uygulama açılmaz.
- **Tür tespiti:** dosyanın ilk baytlarına bakılır (`FileType`): PDF `%PDF-`, JPEG `FF D8 FF`, PNG imzası. İstemcinin bildirdiği `Content-Type` ve dosya adı yok sayılır.
- **Boyut:** üst sınır `bahceden.storage.max-document-bytes` ayarındadır (10 MB). Spring multipart sınırı da buna göre ayarlıdır.
- **Sunum:** sahibine `GET /api/v1/sellers/me/document`, admin'e `GET /api/v1/admin/sellers/{id}/document`. Yanıt başlıkları:
  - tespit edilen `Content-Type`;
  - `X-Content-Type-Options: nosniff`;
  - `Content-Security-Policy: sandbox`;
  - `Cache-Control: no-store, private`;
  - sunucunun ürettiği bir adla `Content-Disposition: inline`.

  Başlıklar tek yerde kurulur (`SellerDocumentResponses`). `web` BFF'i yalnızca bu başlıkları tarayıcıya iletir.

| Alternatif | Neden reddedildi |
|---|---|
| Genel bir `stored_files` tablosu | Herkese açık ve gizli dosyalar aynı yerde durur; bir süzgeç unutulursa gizli belge herkese açık uçtan sunulur. |
| Yeniden gönderimde eski belgeyi silmek | Onayın dayandığı belge kaybolur. Saklama süresi ayrı bir hukuk kararı (KALANLAR A5). |
| Apache Tika ile tür tespiti | K7 dışında büyük bir bağımlılık; üç tür için gereksiz. |
| Belgeyi her zaman `attachment` olarak indirtmek | Admin her belgeyi indirip ayrı açmak zorunda kalır; korumalı başlıklarla `inline` gösterim güvenli. |

## Consequences

### Olumlu
- Gizli belge herkese açık bir uçtan sunulamaz (ayrı tablo, ayrı uçlar).
- İçeriği PDF olmayan `.pdf` dosyası kabul edilmez; kabul edilen dosya da tarayıcıda betik çalıştıramaz.

### Olumsuz
- Antivirüs taraması yok. Kabul edilen bir risk olarak `00-current-state.md`'de izleniyor.
- Belgeler birikir; saklama süresi belirlenene kadar silinmez.

### Nötr / Trade-off
- Ürün fotoğrafları aynı `FileStorage` sınıfını kullanacak, ama kendi tablosu ve kendi herkese açık ucuyla.

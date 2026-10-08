# [ADR-051] Fotoğraflar kayıtlı anahtarla herkese açık sunulur; `web` akış aktarır, `next/image` yeniden işlemez

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-product-listings` design.md D5

---

## Context

Tarayıcı `api`'ye konuşmaz (`AGENTS.md` §12.2). Fotoğraflar herkese açık ve en çok istenen içeriktir; CDN yok ([ADR-039](039-uploads-on-local-volume-served-by-api.md)). Satıcı belgeleri, fotoğraflarla aynı depolama dizininde ve aynı anahtar biçimiyle durur ([ADR-045](045-seller-documents-table-content-sniffing-protected-serving.md)).

## Decision

- `api`, fotoğrafı `GET /api/v1/product-photos/{key}` ucundan herkese açık sunar. Yanıt başlıkları: `image/jpeg`, `Cache-Control: public, max-age=31536000, immutable`, `nosniff`. Anahtar 128 bit rastgele ve değişmezdir.
- **Anahtar `product_photos` tablosunda kayıtlı olmalıdır.** Uç anahtarı önce `card_key` / `large_key` olarak arar; bulamazsa dosya diskte olsa bile 404 döner. Böylece satıcı belgesinin anahtarını bilen biri gizli belgeyi bu uçtan indiremez. Kural `critical` testle sabittir.
- `web`'de `/media/product-photos/[key]` Route Handler'ı fotoğrafı akış olarak ve aynı başlıklarla aktarır (belge aktarımıyla aynı yardımcı). Ekranlar `next/image`'ı bu göreli adresle ve `unoptimized` olarak kullanır; ölçüler sözleşmeden gelir.
- Sözleşme dosya yolu taşımaz, yalnızca anahtarları taşır (`ProductPhotoView`). Adresi `web` kurar.

| Alternatif | Neden reddedildi |
|---|---|
| `next/image` `remotePatterns` ile `api`'nin iç adresi | İç ana bilgisayar adı tarayıcının gördüğü adrese sızar; görüntü iki kez işlenir. |
| Sıralı `photoId` ile sunmak | Taslak fotoğraflar sayılarak taranabilir. |
| Yalnızca anahtar biçimini denetlemek | Gizli belgeler fotoğraf ucundan indirilebilir. |

## Consequences

### Olumlu
- Bir yıllık değişmez önbellek, CDN olmadan da tekrar istekleri tarayıcıda tutar.
- Gizli belge ile herkese açık fotoğraf aynı dizinde dursa da ayrışır.

### Olumsuz
- Her fotoğraf isteği bir veritabanı sorgusu yapar (anahtar denetimi).
- Kaldırılan ilanın fotoğrafı, anahtarı bilen biri için önbellekte görünür kalabilir.

### Nötr / Trade-off
- Fotoğraf `api` → `web` → tarayıcı yolunu izler; ölçek gerektiğinde önüne bir CDN konabilir (B5).

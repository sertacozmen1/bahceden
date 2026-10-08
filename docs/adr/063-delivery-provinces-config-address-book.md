# [ADR-063] Teslimat illeri yapılandırmadan (İstanbul, İzmir, Ankara, Manisa); il kataloğu sabit; hesabın en çok 10 adresi var

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-cart-and-checkout` design.md D5; `openspec/KALANLAR.md` A4 (teslimat bölgesi) — ürün sahibinin 2026-10-08 kararı

---

## Context

KALANLAR A4 teslimat bölgesini soruyordu: Türkiye geneli mi, sınırlı bölge mi? Ürün sahibi sınırlı başlamayı seçti: İstanbul, İzmir, Ankara ve Manisa. Liste büyüyecek; il eklemek kod değişikliği olmamalı. Adres kişisel veridir (K10).

## Decision

- **Teslimat bölgesi:** başlangıçta yalnızca İstanbul (34), İzmir (35), Ankara (06), Manisa (45). Liste `bahceden.commerce.delivery-provinces` yapılandırmasındadır.
- **İl kataloğu:** `common/geo/TurkishProvinces`, 81 il plaka koduyla. Açılışta yapılandırmadaki her kodun katalogda olduğu doğrulanır; yazım hatası açılışta görünür. İl adı özel isimdir, çevrilmez. `GET /api/v1/delivery/provinces` herkese açıktır.
- **Adres defteri:** `addresses` tablosu (başlık, alıcı adı, telefon, il kodu, ilçe, açık adres, isteğe bağlı posta kodu); hesap başına en çok 10 adres (422 `ADDRESS_LIMIT_REACHED`). Telefon satıcı başvurusuyla aynı kuralla E.164'e çevrilir (ortak `common/text/Phones`). Sahiplik sorgu seviyesindedir; başkasının adresi 404.
- Teslimat listesinde olmayan ilde adres kaydedilemez (400, `provinceCode`). İl sonradan listeden çıkarılırsa adres kayıtlı kalır, `deliverable = false` döner ve siparişte kullanılamaz (422 `ADDRESS_NOT_DELIVERABLE`).
- Sipariş adresin kopyasını taşır; adres silinse de geçmiş sipariş etkilenmez.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Türkiye geneli | Ürün sahibi taze ürün ve kargo süresi için sınırlı başlamayı seçti. |
| İl ve ilçe tablosu, ilçe kataloğu | 900'den fazla ilçe bakımı; entegratör ilçeyi serbest metin alır, doğrulama gönderi change'inde. |
| Adresi yalnızca siparişte almak | Tekrar alışverişte adres her seferinde yeniden yazılır. |
| İl kodunu enum yapmak | 81 değer kodda; teslimat listesi yine yapılandırmada. Katalog yeterli. |

## Consequences

### Olumlu
- Yeni il, yapılandırma değişikliğiyle açılır.
- Alıcı adresini bir kez kaydeder.

### Olumsuz
- Listede olmayan illerden alıcılar bugün sipariş veremez.
- İlçe doğrulanmaz; yanlış yazım gönderi change'ine kadar fark edilmez.

### Nötr / Trade-off
- KALANLAR A4'te mobil uygulama sorusu açık kalır.

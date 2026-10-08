# [ADR-041] Mevcut satıcılar onaylı sayılır; vitrin satıcı durumunu sorguda filtreler

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-seller-onboarding` design.md D2

---

## Context

[ADR-038](038-listings-publish-immediately-with-admin-takedown.md) §4: satıcısı `APPROVED` olmayan ürünler vitrinde görünmez. Satıcı askıya alınıp sonra askıdan çıkarılabilir; bu durumda ürünlerinin eski hâline dönmesi gerekir.

`sellers` tablosunda bugün yalnızca dev seed'inden gelen satırlar var ve vitrin onları gösteriyor.

## Decision

- **`status` kolonu `APPROVED` varsayılanıyla eklenir**, böylece mevcut satırlar onaylı olur. Ardından varsayılan **kaldırılır**: yeni kayıtların durumunu uygulama açıkça yazar.
- **Vitrin sorgusu (`ProductRepository.findPublished`) ve sayım sorgusu `seller.status = APPROVED` koşulunu taşır.** Kural ürünün durumuna değil sorguya yazılıdır.
- Vitrin indeksi değişmez. MVP hacminde mevcut indeksler birleştirme için yeterli; ölçüm aksini gösterirse yeni bir changeset gelir.

| Alternatif | Neden reddedildi |
|---|---|
| Askıda satıcının ürünlerini `ARCHIVED`'a çekmek | Askıdan çıkarmada hangi ürünün önceden yayında olduğu bilinmez; toplu yazma gerekir. |
| Mevcut satırları `PENDING` yapmak | Dev vitrini boşalır; seed satıcılarını onaylayacak hesap da yok. |
| Varsayılanı (`APPROVED`) bırakmak | Durumu unutan bir kod satıcıyı sessizce onaylı açar. |

## Consequences

### Olumlu
- Askıdan çıkan satıcının ürünleri kendiliğinden geri gelir; ürün satırlarına dokunulmaz.
- Varsayılan olmadığı için yanlış durumla kayıt açılamaz.

### Olumsuz
- Vitrin sorgusu bir birleştirme koşulu daha taşır. Bu koşul `critical` testle sabitlendi; sorgu sayısı testi hâlâ geçiyor.
- `002` dev seed'i bu changeset'ten sonra yeniden çalışacak biçimde değiştirilirse `status` vermek zorundadır.

### Nötr / Trade-off
- Ürün detay ucu geldiğinde aynı koşulu taşımak zorundadır.

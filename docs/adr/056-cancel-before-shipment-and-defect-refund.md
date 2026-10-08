# [ADR-056] Kargodan önce iptal tam iadedir; teslimden sonra cayma yok, ayıplı ürün 48 saat içinde fotoğrafla bildirilir

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `openspec/KALANLAR.md` A1 (iade ve kısmi iade kuralı); ürün sahibinin 2026-10-08 kararı

---

## Context

Bahçeden taze meyve ve sebze satar. Mesafeli Sözleşmeler Yönetmeliği çabuk bozulabilen veya son kullanma tarihi geçebilecek malların teslimine ilişkin sözleşmeleri cayma hakkının istisnaları arasında sayar. Ayıplı mal için alıcının yasal hakları (6502 sayılı Tüketicinin Korunması Hakkında Kanun) ise her durumda geçerlidir.

İade parası [ADR-054](054-split-payment-via-provider-port-provider-pending.md) gereği ödeme sağlayıcısı üzerinden döner. Kısmi iade, satıcı hakedişini ve komisyonu ([ADR-055](055-commission-percent-plus-fixed-from-seller.md)) etkiler.

Satıcı ürünü, ilanında beyan ettiği süre içinde kargoya vermekle yükümlüdür ([ADR-058](058-flat-shipping-fee-per-seller-and-declared-handover.md)).

## Decision

1. **Kargoya verilmeden önce alıcı satıcı siparişini iptal edebilir.** İptal tam iadedir: ürün tutarı ve kargo ücreti alıcıya döner; komisyon ve hakediş oluşmaz. Satıcı da kargoya vermeden önce gerekçeyle iptal edebilir (stok tükendi, ürün bozuldu); aynı tam iade uygulanır.
2. **Satıcı beyan ettiği kargoya verme süresini aşarsa** sipariş hâlâ kargoya verilmemişse alıcı iptal eder; tam iade.
3. **Teslimden sonra cayma hakkı yoktur** (çabuk bozulan mal istisnası). Bu bilgi sipariş öncesinde alıcıya açıkça gösterilir.
4. **Ayıplı veya hasarlı ürün:** alıcı teslimden itibaren **48 saat** içinde, en az bir fotoğrafla ve satır bazında talep açar. Satıcı talebi kabul edip tam veya kısmi iade önerir ya da gerekçeyle reddeder. Alıcı kabul etmezse veya satıcı süresinde yanıt vermezse **admin karar verir**.
5. **Kısmi iade satır ve tutar bazındadır.** İade edilen ürün tutarı satıcı hakedişinden düşer; komisyonun yüzde kısmı iade edilen tutar oranında geri hesaplanır, sabit ücret yalnızca tam iadede geri alınır. Kargo ücreti yalnızca tam iadede alıcıya döner.
6. Sipariş ve gönderim kayıtları silinmez; iptal ve iade bir durum geçişidir (`AGENTS.md` §11.4).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Yalnızca kargo öncesi iptal, teslim sonrası sistem dışı | Ayıplı mal hakkı yasal zorunluluktur; sistem dışına itmek anlaşmazlığı izsiz bırakır. |
| Yalnızca tam iade (kısmi yok) | Tek bir ezik kasa için tüm siparişin iadesi satıcıya orantısız yük. |
| Teslim sonrası 14 gün cayma | Taze ürün için yönetmelik istisnası var; geri gönderilen ürün satılamaz. |
| Otomatik iade (satıcı onayı olmadan) | Kötüye kullanıma açık; MVP'de admin hakemliği yeterli. |

## Consequences

### Olumlu
- Yasal ayıplı mal hakkı ürün içinde, izli ve fotoğraflı çözülür.
- Kargo öncesi iptal hem alıcıyı hem satıcıyı (stok tükenmesi) korur.

### Olumsuz
- İade ve itiraz akışı sipariş durum makinesine dal ekler; admin için yeni bir karar yüzeyi gerekir.
- Kısmi iadede komisyonun geri hesaplanması `critical` test gerektiren ek bir para kuralıdır.
- 48 saat ve "süresinde yanıt" bir saat kaynağı ve zamanlanmış kontrol ister.

### Nötr / Trade-off
- Ayıplı ürünün kargo firmasına rücuu (kırık, ezik teslim) sistem dışıdır.
- Cayma istisnasının ürün ve metin düzeyindeki uygulaması hukuk teyidine açıktır (KALANLAR A5 ile birlikte).

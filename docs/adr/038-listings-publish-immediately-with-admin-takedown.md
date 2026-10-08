# [ADR-038] Onaylı satıcının ilanı hemen yayına girer; admin gerekçeyle kaldırabilir

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `openspec/KALANLAR.md` A3 (ilan ön onayı); ürün sahibinin 2026-10-08 kararı

---

## Context

[ADR-037](037-seller-application-with-document-and-manual-approval.md) satıcıyı manuel admin onayından geçiriyor. Açık kalan soru şu: onaylı bir satıcının her ilanı da yayına girmeden önce incelenmeli mi?

Satıcı için en kritik akış, ürün eklemektir. Bu akış en kısa yol olmak zorunda (`AGENTS.md` §12.5). Satıcılar sahada, telefondan çalışıyor. Taze ürünün satış penceresi kısa: bugün hasat edilen ürün, ön onay sırasında bekleyen her saat değer kaybeder.

Öte yandan vitrin, yanlış ya da yanıltıcı ilanlara karşı korunmalı (yanlış ürün, yasaklı içerik, yanıltıcı fiyat).

## Decision

1. **`APPROVED` durumundaki satıcının ilanı kaydedildiği anda yayına girer.** İlan için ön onay adımı yoktur.
2. **Satıcı kendi ilanını yayından çekip geri alabilir** (taslak / yayında). Sahiplik sorgu seviyesinde kontrol edilir ([ADR-007](007-query-level-ownership-scoping.md)).
3. **Admin herhangi bir ilanı gerekçe yazarak yayından kaldırabilir.** Kaldırılan ilanı satıcı kendisi tekrar yayına alamaz. Satıcı ilanı düzeltir, admin yeniden açar. Kaldırma gerekçesi satıcıya gösterilir ve kayıt saklanır.
4. **Satıcı `APPROVED` değilse ilanı da vitrinde görünmez.** Bu durumlar `PENDING`, `REJECTED` ve `SUSPENDED`'dır. Vitrin sorgusu ilan durumuyla birlikte satıcı durumunu da filtreler.
5. Geçmiş siparişlerin bağlı olduğu ilan silinmez; arşivlenir (`AGENTS.md` §11.4).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Her ilan ön onaylı (`PENDING_REVIEW`) | Taze ürün beklerken değer kaybeder. Admin her ilanda darboğaza dönüşür. Satıcının en kritik akışı uzar. |
| Yalnızca ilk ilan ön onaylı | Durum makinesine yalnızca bir kez kullanılan bir dal ekler. Satıcı zaten belgeyle onaylandığı için ilk ilanın incelenmesi sınırlı bir güvence getirir. |
| Admin kaldırma yetkisi olmadan tam serbestlik | Yanıltıcı veya yasaklı bir ilan vitrinde kalır. Platformun yasal sorumluluğu karşılanmamış olur. |

## Consequences

### Olumlu
- Satıcı ürünü ekler eklemez satışa açar. Kritik akış en kısa yolunda kalır.
- Admin yükü başvuru incelemesiyle ve şikâyet üzerine müdahaleyle sınırlı kalır.
- İlan durum makinesi küçük kalır: taslak, yayında, admin tarafından kaldırıldı ve arşiv.

### Olumsuz
- Uygunsuz bir ilan, admin fark edene kadar vitrinde görünebilir. MVP'de otomatik içerik denetimi yoktur.
- Vitrin sorgusu satıcı durumunu da filtrelemek zorundadır. Bu filtre her liste ve detay ucunda `critical` testle sabitlenir.

### Nötr / Trade-off
- İlan şikâyet etme (alıcının bildirmesi) bu kararın kapsamında değil. İhtiyaç doğarsa ayrı bir change konusudur.

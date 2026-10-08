# [ADR-055] Komisyon satıcı payından alınır: ürün tutarının %5'i + satıcı siparişi başına 3 TL; değerler siparişe yazılır

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `openspec/KALANLAR.md` A1 (komisyon oranı, kimden kesileceği); ürün sahibinin 2026-10-08 kararı

---

## Context

[ADR-054](054-split-payment-via-provider-port-provider-pending.md) bölünmüş ödemeyi seçti: her ödemede satıcı payı ve pazaryeri payı ayrılır. Pazaryeri payının nasıl hesaplanacağı açıktı.

Bir alıcı sepetinde birden çok satıcının ürünü olabilir. Her satıcı kendi ürününü ayrı bir kargoyla gönderir ([ADR-058](058-flat-shipping-fee-per-seller-and-declared-handover.md)). Bu yüzden komisyonun hesaplandığı birim, sepetin bir satıcıya düşen kısmıdır (bundan sonra **satıcı siparişi**).

Küçük sepetlerde ödeme sağlayıcısının işlem başı maliyeti yüzdeyle karşılanmayabilir. Ürün sahibi yüzdeye ek olarak sabit bir ücret istedi.

Para hesabı `AGENTS.md` §11.3 ve [ADR-006](006-money-as-minor-unit.md) gereği kuruş cinsinden `long` ile yapılır; yuvarlama tek bir yardımcıda ve `critical` testle sabittir.

## Decision

1. **Komisyon satıcının payından düşer.** Alıcı ürün fiyatını ve kargo ücretini görür; komisyon alıcıya ayrı kalem olarak yansımaz.
2. **Formül, satıcı siparişi başına:** `komisyon = yuvarla(ürün tutarı × %5) + 300 kuruş`. Ürün tutarı satıcı siparişindeki satırların toplamıdır; kargo ücreti dahil değildir. Yuvarlama kuruşa, yarım yukarı (`HALF_UP`).
3. **Komisyon ürün tutarını aşamaz.** Formül ürün tutarından büyük çıkarsa komisyon ürün tutarına eşitlenir; satıcı hakedişi negatif olamaz.
4. **Satıcı hakedişi = ürün tutarı − komisyon.**
5. **Oran ve sabit ücret yapılandırmadadır** (`@ConfigurationProperties`, açılışta doğrulanır). Sipariş oluştuğu anda geçerli oran, sabit ücret ve hesaplanan komisyon satıcı siparişine yazılır; sonradan yapılandırma değişse de geçmiş siparişin tutarı değişmez.
6. Komisyon sunucuda hesaplanır; istemciden alınmaz (§11.1).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Yalnızca yüzde (tek oran) | Küçük sepetlerde sağlayıcı işlem maliyeti pazaryerinde kalır. |
| MVP'de komisyon yok | Gelir modeli test edilmez; hesap altyapısı yine gerekir. Ürün sahibi tercih etmedi. |
| Komisyonu alıcıdan ayrı kalem almak | Alıcı için fiyat karmaşıklaşır; büyük pazaryerlerinde alışılmış desen satıcıdan kesintidir. |
| Kategori veya satıcı bazlı farklı oran | Kategori yok; satıcı başına oran bugün gerekmeyen bir yapılandırma katmanı (§3). |
| Komisyonu sipariş anında değil, ödeme anında yapılandırmadan okumak | Oran değişirse alıcının gördüğü ile satıcıya yansıyan tutar ayrışır; siparişe yazmak denetim izini sabitler. |

## Consequences

### Olumlu
- Satıcının neyi neden ödediği tek formülle açıklanır; panelde satır satır gösterilebilir.
- Geçmiş siparişler yapılandırma değişikliğinden etkilenmez.

### Olumsuz
- Sabit 3 TL, çok küçük sepetlerde satıcı için orantısız ağırlık taşır. Asgari sepet tutarı bu kararın kapsamında değil.
- Sağlayıcının kendi işlem ücretinin bu komisyonu aşmaması ticari bir varsayımdır; sağlayıcı seçilince teyit edilir.

### Nötr / Trade-off
- Kampanya, indirim kodu ve satıcıya özel oran kapsam dışı; gerekirse ayrı ADR.

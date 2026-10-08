# [ADR-058] Kargo ücreti satıcı başına sabit 99 TL, satıcının ürün toplamı 1000 TL ve üzeriyse ücretsiz; maliyet pazaryerinde; kargoya verme süresini satıcı ilanda beyan eder

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `openspec/KALANLAR.md` A2 (ücret sahipliği, teslim süresi taahhüdü); ürün sahibinin 2026-10-08 kararı

---

## Context

Gönderiyi pazaryeri entegratör üzerinden açar ve maliyeti pazaryerine faturalanır ([ADR-057](057-shipping-via-integrator-port-integrator-pending.md)). Alıcıdan ne alınacağı ve maliyetin kimde kalacağı açıktı.

Sepet birden çok satıcının ürününü içerebilir; her satıcı ayrı paket gönderir. Taze ürünün kargoya verilme hızı ürüne göre değişir (bugün hasat edilen marul ile kasada bekleyebilen patates aynı değil).

## Decision

1. **Alıcı satıcı başına bir kargo ücreti öder: 99 TL** (9900 kuruş).
2. **Aynı satıcının ürün toplamı 1000 TL (100000 kuruş) veya üzerindeyse o satıcının kargosu ücretsizdir.** Eşik satıcı siparişinin ürün tutarına bakar; kargo ücreti ve diğer satıcıların ürünleri hesaba katılmaz.
3. **Ücret ve eşik yapılandırmadadır**; sipariş oluştuğu anda geçerli değerler satıcı siparişine yazılır (komisyonla aynı ilke, [ADR-055](055-commission-percent-plus-fixed-from-seller.md)).
4. **Gerçek kargo maliyetini pazaryeri karşılar.** Alıcının ödediği kargo ücreti bölünmüş ödemede pazaryeri payına eklenir ([ADR-054](054-split-payment-via-provider-port-provider-pending.md)); ücretsiz kargoda maliyet pazaryerinde kalır. Satıcı hakedişi kargodan etkilenmez: `ürün tutarı − komisyon`.
5. **Satıcı her ilanda kargoya verme süresini beyan eder: 1, 2 veya 3 iş günü.** Süre ilan sayfasında ve sepette görünür. Satıcı siparişinin süresi, içindeki ilanların en uzun süresidir. Süre aşılır ve sipariş hâlâ kargoya verilmemişse alıcı iptal edebilir ([ADR-056](056-cancel-before-shipment-and-defect-refund.md) §2).
6. **Yolda bozulma** ayıplı ürün akışıyla çözülür (48 saat, fotoğraflı); ürünün sorumluluğu satıcıdadır. Kargo firmasına rücu sistem dışıdır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Satıcı kendi kargo ücretini ve eşiğini belirler | Satıcı başına farklı fiyat alıcı için öngörülemez; gönderi pazaryerinin sözleşmesiyle açıldığı için maliyet zaten pazaryerinde. |
| Kargo hep ücretsiz, satıcı fiyata katar | Bölgeye göre maliyet farkı satıcıda kalır; sabit ücret alıcıya şeffaf. |
| Ücretsiz kargoda maliyeti satıcıya yansıtmak | Satıcı hakedişi değişken olur ve sipariş anında bilinmez; ürün sahibi maliyeti pazaryerinde tutmayı seçti. |
| Eşiği tüm sepete uygulamak | Her satıcı ayrı paket gönderir; eşik paket başına anlamlı. |
| Pazaryeri sabit kargoya verme süresi (örn. 2 iş günü) | Taze ürünün hazırlık süresi ürüne göre değişir; satıcının beyanı daha dürüst. |
| Süre aşılınca otomatik iptal | Zamanlanmış iş gerektirir ve alıcı beklemeye razı olabilir; karar alıcıda. |

## Consequences

### Olumlu
- Alıcı kargo ücretini sepette önceden ve tek kuralla bilir.
- Satıcının hakedişi sipariş anında kesindir.
- İlan başına süre, alıcıya ürünün ne zaman çıkacağını dürüstçe söyler.

### Olumsuz
- Ücretsiz kargo ve 99 TL'yi aşan gerçek maliyet (uzak bölge, ağır desi) pazaryerinin zararıdır. Desi ve bölgeye göre ücret bu kararın kapsamında değil; entegratör fiyatları gelince yeniden değerlendirilir.
- İlana yeni bir alan (kargoya verme süresi) eklenir; mevcut ilanlar için varsayılan değer gerekir.
- İş günü hesabı resmi tatil takvimine bağlıdır; MVP'de hafta sonu dışlanır, resmi tatiller yapılandırmayla eklenir ya da göz ardı edilir (sipariş change'inde karar verilir).

### Nötr / Trade-off
- Kargo ücreti alıcıya fatura kalemi olarak pazaryeri adına yansır; fatura ve e-arşiv konusu bu kararın kapsamında değil.

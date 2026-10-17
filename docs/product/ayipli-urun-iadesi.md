# Ayıplı Ürün İadesi

**Tarih:** 2026-10-13
**Sahibi:** Ürün sahibi
**Durum:** Geliştirmede (change `add-defect-claims`)

## 1. Kullanıcı Problemi ve Değer Önerisi

Taze ürün yolda ezilebilir, bozulabilir ya da eksik gelebilir. Bugün alıcı teslimden sonra sistemde hiçbir şey yapamıyor. Taze ürün için cayma hakkı yoktur, ama ayıplı mal hakkı yasal bir zorunluluktur (6502 sayılı Kanun, [ADR-056](../adr/056-cancel-before-shipment-and-defect-refund.md)). Sorun e-postada ya da telefonda çözülünce iz kalmıyor ve satıcı ile alıcı arasındaki anlaşmazlığa kimse hakemlik edemiyor.

- *Bir alıcı olarak, gelen ezik kasanın fotoğrafını çekip yalnızca o ürünün parasını geri istemek istiyorum; böylece tüm siparişi iade etmek ya da satıcıyla telefonda tartışmak zorunda kalmam.*
- *Bir üretici olarak, haklı talebi tek dokunuşla kabul etmek, abartılı talebe adetle karşı teklif vermek ve hakedişimin ne olacağını önceden görmek istiyorum.*
- *Bir admin olarak, anlaşamayan tarafların talebini fotoğraflarıyla görüp adil bir kararı tek ekranda vermek istiyorum.*

**Değer:**
- Alıcıya iki gün içinde, fotoğrafla ve satır bazında bir hak arama yolu.
- Satıcıya kendi kararını verme ve tüm siparişi değil yalnızca sorunlu kısmı iade etme imkânı.
- Pazaryerine izli, gerekçeli ve süreli bir anlaşmazlık çözümü.

## 2. Pazar ve Rakip Analizi

| Desen | Büyük pazaryerlerinde | Bahçeden |
|---|---|---|
| Hasarlı ürün bildirimi | Teslimden sonra belirli bir süre içinde fotoğraflı "hasarlı ürün" talebi | 48 saat, 1–3 fotoğraf, satır ve adet ([ADR-081](../adr/081-defect-claim-opening-window-lines-photos.md)) |
| Ürünün geri gönderilmesi | Çoğunlukla iade kargosuyla ürün geri istenir | Taze ürün geri dönmez; fotoğraf kanıttır |
| Satıcının yanıtı | Satıcı onaylar ya da itiraz eder; pazaryeri hakem olur | Kabul, adetli karşı teklif ya da gerekçeli ret; süre dolarsa otomatik sonuç ([ADR-080](../adr/080-defect-claim-tables-and-state-machine.md), [ADR-083](../adr/083-defect-claim-deadlines-by-scheduled-sweep.md)) |
| Kısmi iade | Çoğunlukla ürün başına tam iade | Adet bazında; komisyon kalan tutar üzerinden yeniden hesaplanır ([ADR-082](../adr/082-defect-refund-amount-and-commission-recalculation.md)) |

**Fark:** ürün geri gelmeden, adet bazında ve iki tarafa da süreli bir çözüm.

## 3. Kapsam ve Kabul Kriterleri (Acceptance Criteria)

Kriterler `openspec/specs/defect-claims/spec.md` (change: `openspec/changes/archive/2026-10-14-add-defect-claims/`) senaryolarıyla eşleşir.

- [ ] Alıcı teslimden sonraki 48 saat içinde satır, adet, açıklama ve 1–3 fotoğrafla talep açar; pencere dışında, teslim edilmemiş siparişte ve ikinci talepte reddedilir.
- [ ] Satıcı 48 saat içinde kabul eder, daha az adet önerir ya da gerekçeyle reddeder; para etkisini yanıt vermeden görür.
- [ ] Alıcı teklifi veya reddi 48 saat içinde kabul eder ya da yönetime taşır.
- [ ] Süre dolan talepler kendiliğinden ilerler: satıcı sessizse talep yönetime düşer, alıcı sessizse teklif uygulanır ya da ret kesinleşir. Hiçbir iade iki kez yapılmaz.
- [ ] Admin yönetime taşınan talebi fotoğraflarıyla görür; satır başına adetle iade eder ya da gerekçeyle reddeder.
- [ ] İade tutarı ve yeni hakediş ADR-082 kuralıyla hesaplanır; stok değişmez.
- [ ] Fotoğraflar yalnızca alıcı, satıcı ve admin tarafından görülür.
- [ ] Her adımda ilgili taraflara hesap dilinde e-posta gider.
- [ ] Ekranlar 375 px ve 1440 px'de, açık ve koyu temada çalışır; talep formu telefonda eksiksizdir.

**Kapsam dışı:**
- iade kargosu;
- kargo firmasına rücu;
- ikinci talep veya itiraz;
- satıcı puanı;
- admin bildirimi;
- gerçek sağlayıcıda pay bölüşümü (KALANLAR A1).

## 4. Başarı Metrikleri (KPIs)

| Metrik | Neden önemli |
|---|---|
| Teslim edilen satıcı siparişlerinde talep oranı | Kargo ve paketleme kalitesini, kötüye kullanımı gösterir |
| Satıcının medyan yanıt süresi | Satıcının talebe ilgisini gösterir; 48 saate yakınsa hatırlatma gerekir |
| Yönetime düşme oranı (satıcı sessiz ya da alıcı itiraz) | Admin iş yükünü ve tarafların anlaşma oranını gösterir |
| Tam / kısmi / iadesiz sonuç oranı | Kısmi iadenin gerçekten kullanılıp kullanılmadığını gösterir |

# Sepet ve Sipariş

**Tarih:** 2026-10-08
**Sahibi:** Ürün sahibi
**Durum:** Yayında (change `add-cart-and-checkout`, archive edildi)

## 1. Kullanıcı Problemi ve Değer Önerisi

İlanlar yayında ama alıcı hiçbir şey satın alamıyor: sepet, adres, sipariş ve ödeme yok. Üretici de ürününü satabilmek için parasının nereye ve nasıl geleceğini bilmiyor.

- *Bir alıcı olarak, birkaç üreticinin ürününü tek sepette toplayıp tek ödemeyle almak istiyorum; her üreticinin kargo ücretini ödemeden önce görmek istiyorum.*
- *Bir alıcı olarak, üretici ürünü henüz kargoya vermediyse siparişimi iptal edip paramı geri almak istiyorum.*
- *Bir üretici olarak, ödeme bilgilerimi bir kez girip siparişleri, komisyonu ve elime geçecek tutarı açıkça görmek istiyorum.*

**Değer önerisi:**
- Tek ödemede birden çok üretici: para bölünmüş ödemeyle her üreticiye ayrı ayrılır ([ADR-054](../adr/054-split-payment-via-provider-port-provider-pending.md)).
- Şeffaf kargo ücreti: üretici başına 99 TL, 1000 TL ve üzerinde ücretsiz ([ADR-058](../adr/058-flat-shipping-fee-per-seller-and-declared-handover.md)).
- Üretici komisyonunu ve hakedişini sipariş anında bilir ([ADR-055](../adr/055-commission-percent-plus-fixed-from-seller.md)).
- Kargodan önce iptal tam iadedir ([ADR-056](../adr/056-cancel-before-shipment-and-defect-refund.md)).

## 2. Pazar ve Rakip Analizi

| Kanal | Sepet ve kargo (genel gözlem) | Sonuç |
|---|---|---|
| Genel pazaryerleri | Çok satıcılı sepet; satıcı başına kargo, eşik üstü ücretsiz kargo yaygın | Alıcı için alışılmış desen; Bahçeden aynı deseni izler |
| Üreticinin kendi sitesi | Tek satıcı, kendi ödeme ve kargo düzeni | Alıcı her üretici için ayrı ödeme yapar |
| Sosyal medya ve mesajlaşma | Sipariş ve ödeme elle (havale) | Güvence ve iade yolu yok |

> Tablodaki gözlemler kamuya açık genel bilgilere dayanıyor. 2026-10 itibarıyla rakiplerin güncel akışları tek tek doğrulanmadı.

**Ayırt edici nokta:** Taze ürüne özgü kurallar: üretici her ilanda kargoya verme süresini beyan eder, sepet ve sipariş bu süreyi gösterir.

## 3. Kapsam ve Kabul Kriterleri

Davranışın tamamı `openspec/specs/` altındaki `seller-payout-details`, `cart-and-checkout`, `orders` dosyalarındadır (archive'dan önce change'in delta spec'leri). Özet:

- [ ] Onaylı satıcı ödeme bilgilerini (IBAN, TCKN veya VKN, yasal ad) girer; değerler doğrulanır ve maskeli gösterilir.
- [ ] Vitrin her ürünün satın alınabilir olup olmadığını ve nedenini gösterir; ilanlar kargoya verme süresini taşır.
- [ ] Oturum açmış alıcı sepete ekler; sepet üreticilere göre gruplanır, kargo ücreti ve toplam sunucudan gelir.
- [ ] Alıcı yalnızca teslimat yapılan illerde (İstanbul, İzmir, Ankara, Manisa) adres kaydeder.
- [ ] Sipariş üretici siparişlerine bölünür; fiyat, kargo, komisyon ve hakediş sipariş anında sabitlenir; çift tıklama iki sipariş açmaz.
- [ ] Ödeme sağlayıcıdan doğrulanır; stok ödeme başarılı olunca düşer; stok yetmezse para tamamen iade edilir.
- [ ] Alıcı ve satıcı siparişlerini görür; satıcı alıcının yalnızca il ve ilçesini görür.
- [ ] Kargoya verilmemiş üretici siparişi alıcı veya satıcı tarafından iptal edilir; tam iade, stok geri gelir, karşı tarafa e-posta gider.
- [ ] Ekranlar telefonda eksiksiz çalışır (375 px) ve iki dilde metin taşır.

**Kapsam dışı:** kargo gönderisi ve takibi, ayıplı ürün iadesi, gerçek ödeme sağlayıcısı, misafir sepeti, stok rezervasyonu, kupon, admin sipariş ekranı, fatura. Ayrıntı: change `add-cart-and-checkout` proposal'ının Non-goals bölümü.

## 4. Başarı Metrikleri

| Metrik | Neyi gösterir | Hedef (ilk ölçüm sonrası ayarlanır) |
|---|---|---|
| Sepetten siparişe dönüşüm | Checkout akışının tamamlanabilirliği | %40 üstünde |
| Ödeme başarısızlık oranı | Ödeme adımındaki sürtünme | %10 altında |
| Stok yetersizliği iadesi oranı | Rezervasyonsuz stok modelinin bedeli | %1 altında |
| Satıcı iptal oranı | Stok ve hazırlık disiplininin işleyişi | %3 altında |

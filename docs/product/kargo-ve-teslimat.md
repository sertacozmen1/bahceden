# Kargo ve Teslimat

**Tarih:** 2026-10-10
**Sahibi:** Ürün sahibi
**Durum:** Yayında (change `add-shipments`, archive edildi)

## 1. Kullanıcı Problemi ve Değer Önerisi

Alıcı ödüyor ama sipariş "Hazırlanıyor"da kalıyor: üreticinin paketi gönderecek bir yolu, alıcının da paketini izleyecek bir yeri yok. Üretici çoğu zaman tarlada, elinde yalnızca telefonla çalışıyor; kargo firmasıyla ayrı anlaşma yapması, takip numarası yazması ve alıcıya iletmesi hata ve zaman kaybı demek.

- *Bir üretici olarak, siparişi hazırladığımda tek dokunuşla gönderi açmak, etiketi yazdırmak ya da kodu şubede söylemek istiyorum.*
- *Bir alıcı olarak, paketimin kargoya verilip verilmediğini, hangi firmada olduğunu ve ne zaman teslim edildiğini görmek istiyorum.*
- *Bir pazaryeri yöneticisi olarak, teslim edilemeyen paketlerde alıcıyla üretici arasında adil karar vermek istiyorum.*

**Değer önerisi:**
- Üretici kargo firmasıyla anlaşmaz, takip numarası yazmaz: gönderi sistemden açılır ([ADR-057](../adr/057-shipping-via-integrator-port-integrator-pending.md)).
- Paket şubeye teslim edilir; üreticiden açık adres istenmez.
- Alıcı firma, takip numarası ve durumu sipariş sayfasında görür, kargoya verildiğinde e-posta alır.
- Teslim edilemeyen pakette admin tam iade ya da gerekçeyle kapatma kararı verir.

## 2. Pazar ve Rakip Analizi

| Kanal | Kargo akışı (genel gözlem) | Sonuç |
|---|---|---|
| Genel pazaryerleri | Pazaryeri anlaşmalı kargo; satıcı panelden barkod / gönderi kodu alır, paketi şubeye bırakır veya kapıdan aldırır; durum alıcıya otomatik yansır | Alışılmış desen; Bahçeden aynısını izler |
| Üreticinin kendi sitesi | Üretici kendi kargo sözleşmesiyle gönderir, takip numarasını elle iletir | Hata payı yüksek, takip dağınık |
| Sosyal medya ve mesajlaşma | Takip numarası mesajla iletilir | İz ve güvence yok |

> Tablodaki gözlemler kamuya açık genel bilgilere dayanıyor. 2026-10 itibarıyla rakiplerin güncel akışları tek tek doğrulanmadı.

**Ayırt edici nokta:** Taze ürüne uygun sadelik: üç koli boyutu, telefonda büyük puntolu gönderi kodu, kargoya verme son tarihinin aynı ekranda görünmesi.

## 3. Kapsam ve Kabul Kriterleri

Davranışın tamamı `openspec/specs/shipments` ve `openspec/specs/orders` dosyalarındadır (archive'dan önce change'in delta spec'leri). Özet:

- [ ] Onaylı satıcı ödenmiş satıcı siparişi için koli boyutunu seçerek gönderi açar; tekrar basmak ikinci gönderi açmaz.
- [ ] Etiket yalnızca satıcısına görünür; panelde gönderi kodu büyük puntoyla gösterilir.
- [ ] Gönderi durumu kendiliğinden ilerler; satıcı siparişi "Kargoya verildi", "Teslim edildi" veya "Teslim edilemedi" olur.
- [ ] Alıcı firma, takip numarası ve durumu görür; kargoya verildiğinde e-posta alır.
- [ ] Gönderisi açılmış ama kargoya verilmemiş sipariş iptal edilebilir; gönderi de iptal olur ve tam iade yapılır. Kargoya verilmiş sipariş iptal edilemez.
- [ ] Teslim edilemeyen satıcı siparişinde admin tam iade eder veya gerekçeyle kapatır; iki tarafa e-posta gider.
- [ ] Ekranlar telefonda eksiksiz çalışır (375 px) ve iki dilde metin taşır.

**Kapsam dışı:** ayıplı ürün talebi ve kısmi iade, gerçek kargo entegratörü, desiye göre ücret, kapıdan alım, çoklu koli, SMS. Ayrıntı: change `add-shipments` proposal'ının Non-goals bölümü.

## 4. Başarı Metrikleri

| Metrik | Neyi gösterir | Hedef (ilk ölçüm sonrası ayarlanır) |
|---|---|---|
| Ödemeden kargoya verilmeye ortalama süre | Satıcı akışının hızı | 1,5 iş günü altında |
| Kargoya verme süresi aşım oranı | Beyan edilen sürelerin gerçekçiliği | %5 altında |
| Teslim edilemeyen oranı | Adres kalitesi ve kargo performansı | %2 altında |
| Gönderi iptal oranı (açılıp iptal edilen) | Gönderinin erken açılıp açılmadığı | %5 altında |

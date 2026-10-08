# Ürün İlanları

**Tarih:** 2026-10-08
**Sahibi:** Ürün sahibi
**Durum:** Yayında (change `add-product-listings`, archive edildi)

## 1. Kullanıcı Problemi ve Değer Önerisi

Onaylanan üretici bugün hiçbir şey satamıyor: ürün ekleyecek bir ekran yok. Üretici bahçede, elinde telefonla duruyor. Ürünün fotoğrafını çekip birkaç alan doldurarak dakikalar içinde satışa açabilmeli. Alıcı ise ürünü yalnızca liste kartında görüyor; fotoğrafına, açıklamasına, kimin ürettiğine bakabileceği bir sayfası yok.

- *Bir üretici olarak, telefonumdan ürünümü fotoğraflarıyla ekleyip tek dokunuşla yayına almak istiyorum; böylece hasat ettiğim gün satışa başlayabilirim.*
- *Bir alıcı olarak, ürünün fotoğraflarını ve açıklamasını görmek istiyorum; böylece kimden ne aldığımı bilerek karar veririm.*
- *Bir platform yöneticisi olarak, uygunsuz bir ilanı satıcıyı askıya almadan kaldırabilmek istiyorum.*

**Değer önerisi:**
- İlan ön onaysız yayına girer ([ADR-038](../adr/038-listings-publish-immediately-with-admin-takedown.md)).
- Fotoğraflar sunucuda küçültülür ve telefonun eklediği konum bilgisinden arındırılır.
- Uygunsuz ilan, satıcıyı cezalandırmadan tek başına kaldırılabilir.

## 2. Pazar ve Rakip Analizi

| Kanal | İlan ekleme (genel gözlem) | Üretici için sonuç |
|---|---|---|
| Genel pazaryerleri | Kategori ağacı, barkod, varyant, çok sayıda zorunlu alan | Tek ürün satan bir çiftçi için ağır |
| İlan siteleri | Hızlı, ama ödeme ve gönderim güvencesi yok | Güven ve kayıt eksik |
| Sosyal medya | Fotoğraf + metin; fiyat ve stok yapılandırılmış değil | Alıcı karşılaştıramaz, sipariş elle yürür |

> Tablodaki gözlemler kamuya açık genel bilgilere dayanıyor. 2026-10 itibarıyla rakiplerin güncel ilan akışları tek tek doğrulanmadı.

**Ayırt edici nokta:** Az alan (ad, birim, fiyat, stok, fotoğraf), ön onaysız yayın ve her ilanda üreticinin adı ve şehri.

## 3. Kapsam ve Kabul Kriterleri

Davranışın tamamı `openspec/specs/product-listings/spec.md` ve `storefront-catalog` dosyalarındadır (archive'dan önce change'in delta spec'leri). Özet:

- [ ] Onaylı satıcı taslak ilan oluşturur, düzenler, listeler; başkasının ilanına erişemez.
- [ ] İlana 1–5 fotoğraf eklenir; sıra değiştirilebilir, ilk fotoğraf kapaktır.
- [ ] Fotoğraf sunucuda döndürülür, küçültülür ve meta verisinden arındırılır.
- [ ] Fotoğrafı olan taslak yayına alınır; yayından çekilir, arşivlenir, arşivden geri alınır.
- [ ] Vitrin kartında kapak fotoğrafı; ürün detay sayfasında galeri.
- [ ] Admin ilanı gerekçeyle kaldırır ve yeniden açar; satıcı kaldırılan ilanı kendisi açamaz; karar e-postayla bildirilir.
- [ ] Ekranlar telefonda eksiksiz çalışır (375 px) ve iki dilde metin taşır.

**Kapsam dışı:** kategori, arama, varyant, sipariş ve stok düşme, kargo bilgisi, şikâyet, satıcı profil sayfası. Ayrıntı: change `add-product-listings` proposal'ının Non-goals bölümü.

## 4. Başarı Metrikleri

| Metrik | Neyi gösterir | Hedef (ilk ölçüm sonrası ayarlanır) |
|---|---|---|
| Onaydan ilk yayına geçen süre (medyan) | Satıcının ilan akışında takılıp takılmadığı | 1 gün altında |
| Taslaktan yayına geçme oranı | İlan formunun tamamlanabilirliği | %70 üstünde |
| İlan başına fotoğraf sayısı (ortalama) | Fotoğraf yüklemenin kolaylığı | 2 ve üstü |
| Admin kaldırma oranı | Ön onaysız yayın modelinin işleyip işlemediği | %2 altında |

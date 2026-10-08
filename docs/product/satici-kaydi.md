# Satıcı Kaydı ve Onayı

**Tarih:** 2026-10-08
**Sahibi:** Ürün sahibi
**Durum:** Yayında (change `add-seller-onboarding`, archive edildi)

## 1. Kullanıcı Problemi ve Değer Önerisi

Manisa'daki bir üretici, ürününü hale verdiği fiyatla rafta görülen fiyat arasındaki farkı biliyor, ama kendi satış kanalını kuramıyor. Büyük pazaryerlerine kaydolmak şirket, e-fatura ve muhasebe altyapısı istiyor. Bir çiftçi için bu, başlamadan pes etmek demek.

Platform için de bir problem var: gıda satan bir pazaryeri, satıcının gerçekten üretici olduğunu gösterebilmek zorunda. Herkesin kayıt olup ürün listeleyebildiği bir yapı yasal sorumluluk ve güven sorunu doğurur.

- *Bir üretici olarak, alıcı hesabımla birkaç bilgi ve tek bir belge göndererek satıcı olmak istiyorum; böylece aracısız satışa başlayabilirim.*
- *Bir platform yöneticisi olarak, her satıcının arkasında incelediğim bir belge ve verdiğim bir karar olsun istiyorum; böylece vitrine giren her üreticiden hesap verebilirim.*

**Değer önerisi:** Başvuru, mevcut hesaptan tek bir formla yapılır: üretici adı, şehir, telefon ve ÇKS belgesi veya vergi levhası. Karar e-postayla bildirilir. Reddedilen üretici gerekçeyi görür, düzeltir ve yeniden gönderir.

## 2. Pazar ve Rakip Analizi

| Kanal | Satıcı olma koşulu (genel gözlem) | Üretici için sonuç |
|---|---|---|
| Genel pazaryerleri | Vergi kaydı olan bir işletme ve fatura altyapısı | Vergi kaydı olmayan küçük çiftçi çoğunlukla dışarıda kalır |
| Sosyal medya ve mesajlaşma üzerinden satış | Koşul yok | Güven, ödeme ve kayıt yok; alıcı kimden aldığını doğrulayamaz |
| Hal ve komisyoncu | Koşul yok | Fiyatı üretici belirlemez |

> Tablodaki koşullar kamuya açık genel bilgilere dayanır ve 2026-10 itibarıyla rakiplerin güncel başvuru sayfalarından tek tek doğrulanmadı. Rakip analizi derinleştirilirken bu tablo güncellenir.

**Ayırt edici nokta:** Bahçeden, vergi levhasının yanında ÇKS (Çiftçi Kayıt Sistemi) belgesini de kabul eder ve kararı bir insan verir ([ADR-037](../adr/037-seller-application-with-document-and-manual-approval.md)). Belgenin yasal yeterliliği hukuk teyidi bekliyor.

## 3. Kapsam ve Kabul Kriterleri

Davranışın tamamı `openspec/specs/seller-onboarding/spec.md` dosyasındadır (archive'dan önce change'in delta spec'i). Özet:

- [ ] E-postası doğrulanmış bir hesap tek formla başvurur; kayıt `PENDING` açılır.
- [ ] Belge PDF, JPEG veya PNG olmalıdır; tür içerikten doğrulanır, boyut sınırlıdır.
- [ ] Belgeyi yalnızca sahibi ve admin görür.
- [ ] Admin onaylar, gerekçeyle reddeder, gerekçeyle askıya alır veya askıdan çıkarır.
- [ ] Onayla `ROLE_SELLER` verilir, askıda kaldırılır.
- [ ] Her karar hesabın dilinde e-postayla bildirilir.
- [ ] Reddedilen üretici düzeltip yeniden gönderir.
- [ ] Bütün geçişler silinmeyen bir geçmişte tutulur.
- [ ] Onaylı olmayan satıcının ürünleri vitrinde görünmez.
- [ ] Ekranlar telefonda eksiksiz çalışır (375 px) ve iki dilde metin taşır.

**Kapsam dışı:** ürün ilanı ekleme, profil düzenleme, admin'e yeni başvuru bildirimi, TCKN/VKN/IBAN, otomatik belge doğrulama. Ayrıntı ve gerekçe: change `add-seller-onboarding` proposal'ının Non-goals bölümü.

## 4. Başarı Metrikleri

| Metrik | Neyi gösterir | Hedef (ilk ölçüm sonrası ayarlanır) |
|---|---|---|
| Başvurudan karara geçen süre (medyan) | Manuel onayın üreticiyi ne kadar beklettiği | 2 iş günü altında |
| İlk başvuruda ret oranı | Formun ve belge talimatının anlaşılırlığı | %30 altında |
| Reddedilenlerin yeniden gönderme oranı | Gerekçelerin yol gösterip göstermediği | %50 üstünde |
| Başvuruyu yarıda bırakma | Formun telefonda tamamlanabilirliği | Ölçüm altyapısı yok; analitik kararıyla gelir |

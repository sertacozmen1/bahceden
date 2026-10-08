# storefront-catalog Specification

## Purpose
Vitrinde yayınlanmış ürünlerin, ürünü üreten ve gönderen üreticiyle birlikte, kimlik doğrulaması gerektirmeden listelenmesini tanımlar. Ürün detayı ve kapak fotoğrafı add-product-listings ile, satın alınabilirlik ve kargoya verme süresi add-cart-and-checkout ile eklendi; arama ve filtre bu capability'nin bugünkü kapsamı dışındadır.

## Requirements

### Requirement: Vitrinde yayınlanmış ürünlerin listelenmesi

Sistem, yayın durumu `PUBLISHED` olan **ve satıcısı `APPROVED` durumunda olan** ürünleri kimlik doğrulaması gerektirmeden listelemeyi SHALL sağlar. Liste her ürün için adını, birimini, birim fiyatını, stok durumunu, ürünün üreticisini (ad ve şehir) ve varsa kapak fotoğrafını içerir. `DRAFT`, `ARCHIVED` ve `REMOVED` durumundaki ürünler listede SHALL yer almaz. Satıcısı `PENDING`, `REJECTED` veya `SUSPENDED` durumunda olan ürünler de, kendi yayın durumları ne olursa olsun, listede SHALL yer almaz.

#### Scenario: Yayınlanmış ürünler listelenir
- **WHEN** istemci ürün listesini kimlik bilgisi olmadan talep eder
- **THEN** yalnızca satıcısı `APPROVED` olan `PUBLISHED` ürünler, her biri üretici adı ve şehriyle birlikte döner

#### Scenario: Yayınlanmamış ürün gizlenir
- **WHEN** bir ürün `DRAFT`, `ARCHIVED` veya `REMOVED` durumundadır
- **THEN** o ürün vitrin listesinde görünmez

#### Scenario: Onaylı olmayan satıcının ürünü gizlenir
- **WHEN** `PUBLISHED` bir ürünün satıcısı askıya alınır
- **THEN** ürün vitrin listesinden ve toplam sayıdan düşer; satıcı askıdan çıkarıldığında ürün yeniden görünür

#### Scenario: Kapak fotoğrafı
- **WHEN** listedeki bir ürünün fotoğrafları vardır
- **THEN** ürün sıradaki ilk fotoğrafı kapak olarak taşır; fotoğrafı olmayan ürünün kapak alanı boştur ve liste hata üretmez

#### Scenario: Hiç yayınlanmış ürün yok
- **WHEN** sistemde yayınlanmış ürün bulunmaz
- **THEN** boş bir liste ve toplam sayı sıfır döner; hata üretilmez ve ekranda kullanıcıyı yönlendiren bir boş durum gösterilir

### Requirement: Liste sayfalıdır ve sayfa boyutunun üst sınırı sunucuda uygulanır

Liste ucu `page` (sıfırdan başlar) ve `size` parametrelerini SHALL kabul eder. Varsayılan sayfa boyutu 20, üst sınır 50'dir. Üst sınırı aşan bir `size` değeri reddedilmez; sunucu değeri sınıra çeker ve yanıtta uygulanan gerçek değeri bildirir. Yanıt; içerik dizisi, geçerli sayfa, uygulanan sayfa boyutu, toplam kayıt sayısı ve toplam sayfa sayısını içerir.

#### Scenario: Varsayılan sayfalama
- **WHEN** istemci sayfalama parametresi vermeden liste talep eder
- **THEN** ilk sayfa en çok 20 kayıtla döner ve yanıt uygulanan sayfa boyutunu 20 olarak bildirir

#### Scenario: Üst sınırın üzerinde sayfa boyutu
- **WHEN** istemci `size` değerini 500 olarak gönderir
- **THEN** istek başarılı olur, en çok 50 kayıt döner ve yanıt uygulanan sayfa boyutunu 50 olarak bildirir

#### Scenario: Aralık dışı sayfa
- **WHEN** istemci var olmayan bir sayfa numarası talep eder
- **THEN** boş içerik dizisi ve doğru toplam sayı döner; hata üretilmez

#### Scenario: Geçersiz sayfalama parametresi
- **WHEN** istemci `page` veya `size` için negatif ya da sayı olmayan bir değer gönderir
- **THEN** istek doğrulama hatasıyla reddedilir ve ortak hata gövdesi döner

### Requirement: Liste sabit ve öngörülebilir bir sırada döner

Ürünler yayın tarihine göre **yeniden eskiye** SHALL sıralanır. Eşit yayın tarihlerinde sıralama kararlıdır: ikincil ölçüt ürün tanımlayıcısıdır. İstemci bu dilimde sıralama parametresi göndermez.

#### Scenario: Yeni yayınlanan ürün üstte
- **WHEN** iki ürün farklı tarihlerde yayınlanmıştır
- **THEN** daha yeni yayınlanan ürün listede daha üstte yer alır

#### Scenario: Aynı anda yayınlanan ürünler
- **WHEN** iki ürünün yayın tarihi aynıdır
- **THEN** sıralama sayfalar arasında tutarlıdır ve aynı kayıt iki farklı sayfada tekrarlanmaz

### Requirement: Para değerleri minor unit tamsayı olarak taşınır

Fiyatlar yanıtta tamsayı kuruş değeri ve açık para birimi olarak SHALL döner. İstemci fiyat üzerinde hesap yapmaz; yalnızca biçimlendirir.

#### Scenario: Fiyat biçimi
- **WHEN** bir ürünün fiyatı 125,50 TL'dir
- **THEN** yanıtta tutar 12550 tamsayısı ve para birimi `TRY` olarak döner

### Requirement: Vitrin ekranı yükleniyor, boş ve hata durumlarını gösterir

Vitrin ekranı; veri beklenirken bir yükleniyor durumu, kayıt yokken yönlendirici bir boş durum, veri alınamadığında ne olduğunu ve ne yapılabileceğini söyleyen bir hata durumu SHALL gösterir. Hiçbir durumda boş beyaz sayfa gösterilmez.

#### Scenario: Veri beklenirken
- **WHEN** liste verisi henüz gelmemiştir
- **THEN** ekranda içeriğin yerini tutan bir yükleniyor göstergesi bulunur

#### Scenario: Servis erişilemez
- **WHEN** liste verisi alınamaz
- **THEN** ekranda ne olduğunu açıklayan bir hata mesajı ve yeniden deneme imkânı sunulur; sayfanın tamamı çökmez

#### Scenario: Kayıt yok
- **WHEN** yayınlanmış ürün bulunmaz
- **THEN** ekranda kullanıcıyı yönlendiren bir boş durum metni gösterilir

### Requirement: Ürün detayı

Sistem, vitrinde görünen bir ürünün detayını kalıcı adresiyle (`slug`), kimlik doğrulaması gerektirmeden SHALL sunar. Detay şunları içerir:

- ad ve açıklama;
- birim ve birim fiyat;
- stok durumu;
- üretici (ad ve şehir);
- sıralı fotoğraflar;
- **kargoya verme süresi**;
- **ürünün satın alınabilir olup olmadığı**.

Vitrin listesinde görünmeyecek bir ürünün detayı istendiğinde, ürün yokmuş gibi yanıt verilir.

#### Scenario: Yayındaki ürünün detayı
- **WHEN** istemci yayında olan ve satıcısı onaylı bir ürünün adresini ister
- **THEN** ürün bütün fotoğraflarıyla, sıralı olarak, kargoya verme süresi ve satın alınabilirlik bilgisiyle döner

#### Scenario: Görünmeyen ürünün detayı
- **WHEN** istenen ürün yoktur, taslaktır, arşivdedir, kaldırılmıştır veya satıcısı onaylı değildir
- **THEN** 404 döner ve ekranda "bulunamadı" durumu gösterilir

### Requirement: Satın alınabilirlik

Sistem, vitrin listesindeki ve ürün detayındaki her ürün için satın alınabilir olup olmadığını SHALL bildirir. Bir ürün yalnızca şu dört koşulun hepsi sağlandığında satın alınabilirdir:

1. yayındadır;
2. satıcısı onaylıdır;
3. satıcısının ödeme hesabı etkindir;
4. stoğu sıfırdan büyüktür.

Satın alınamayan ürün vitrinde görünmeye devam eder. Ekranda sepete ekleme eylemi yerine nedeni gösterilir: tükendi ya da satıcı henüz satışa hazır değil.

#### Scenario: Ödeme hesabı olmayan satıcının ürünü
- **WHEN** yayındaki bir ürünün onaylı satıcısının etkin ödeme hesabı yoktur
- **THEN** ürün vitrinde görünür, satın alınamaz olarak işaretlenir ve ekranda "satıcı henüz satışa hazır değil" bilgisi gösterilir

#### Scenario: Tükenen ürün
- **WHEN** yayındaki bir ürünün stoğu sıfırdır
- **THEN** ürün vitrinde görünür, satın alınamaz olarak işaretlenir ve ekranda "tükendi" bilgisi gösterilir

#### Scenario: Satın alınabilir ürün
- **WHEN** ürün dört koşulun hepsini sağlar
- **THEN** ürün satın alınabilir olarak işaretlenir ve detay sayfasında sepete ekleme eylemi gösterilir

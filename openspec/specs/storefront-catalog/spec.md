# storefront-catalog Specification

## Purpose
Vitrinde yayınlanmış ürünlerin, ürünü üreten ve gönderen üreticiyle birlikte, kimlik doğrulaması gerektirmeden listelenmesini tanımlar. Arama, filtre ve ürün detayı bu capability'nin bugünkü kapsamı dışındadır.

## Requirements

### Requirement: Vitrinde yayınlanmış ürünlerin listelenmesi

Sistem, yayın durumu `PUBLISHED` olan **ve satıcısı `APPROVED` durumunda olan** ürünleri kimlik doğrulaması gerektirmeden listelemeyi SHALL sağlar. Liste her ürün için adını, birimini, birim fiyatını, stok durumunu ve ürünün üreticisini (ad ve şehir) içerir. `DRAFT` ve `ARCHIVED` durumundaki ürünler listede SHALL yer almaz. Satıcısı `PENDING`, `REJECTED` veya `SUSPENDED` durumunda olan ürünler de, kendi yayın durumları ne olursa olsun, listede SHALL yer almaz.

#### Scenario: Yayınlanmış ürünler listelenir
- **WHEN** istemci ürün listesini kimlik bilgisi olmadan talep eder
- **THEN** yalnızca satıcısı `APPROVED` olan `PUBLISHED` ürünler, her biri üretici adı ve şehriyle birlikte döner

#### Scenario: Yayınlanmamış ürün gizlenir
- **WHEN** bir ürün `DRAFT` veya `ARCHIVED` durumundadır
- **THEN** o ürün vitrin listesinde görünmez

#### Scenario: Onaylı olmayan satıcının ürünü gizlenir
- **WHEN** `PUBLISHED` bir ürünün satıcısı askıya alınır
- **THEN** ürün vitrin listesinden ve toplam sayıdan düşer; satıcı askıdan çıkarıldığında ürün yeniden görünür

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

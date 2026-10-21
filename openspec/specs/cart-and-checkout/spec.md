# cart-and-checkout Specification

## Purpose
Hesaba bağlı sepeti, satıcı başına kargo ücretini, teslimat illerini ve adresleri, sepetten sipariş oluşturmayı (e-posta doğrulaması koşulu, satıcı siparişlerine bölme, tutarların sabitlenmesi, tekrar anahtarı) ödemenin sağlayıcıdan doğrulanıp stoğun ödeme anında düşmesini ve dönüşü gelmeyen ödenmemiş siparişlerin zamanlanmış işle kapatılmasını tanımlar. Kararlar: ADR-054…058, ADR-059, ADR-061…066, ADR-093, ADR-094, ADR-096. Kargo gönderisi ve gerçek ödeme sağlayıcısı bu capability'nin bugünkü kapsamı dışındadır.

## Requirements

### Requirement: Sepet hesaba bağlıdır ve satıcılara göre gruplanır

Sistem, oturum açmış her hesap için tek bir sepet SHALL tutar. Sepet yalnızca ürün ve adet saklar. Fiyat, stok ve satın alınabilirlik her okumada üründen alınır. Sepet satıcılara göre gruplanmış döner. Her grupta şunlar bulunur:

- ürün ara toplamı;
- kargo ücreti;
- ücretsiz kargoya kalan tutar.

Sepetin genel toplamı da yanıtta yer alır. Tutarların tamamını sunucu hesaplar.

#### Scenario: Ürünü sepete ekleme
- **WHEN** alıcı satın alınabilir bir üründen stoğu aşmayan bir adet ekler
- **THEN** satır sepete eklenir ve sepet güncel tutarlarla döner

#### Scenario: Oturum olmadan sepet
- **WHEN** kimlik bilgisi olmadan sepet okunur veya değiştirilir
- **THEN** istek 401 ile reddedilir

#### Scenario: Satın alınamayan ürün
- **WHEN** alıcı yayında olmayan, satıcısı onaylı olmayan, satıcısının ödeme hesabı etkin olmayan veya stoğu tükenmiş bir ürünü eklemeye çalışır
- **THEN** istek iş kuralı hatasıyla reddedilir ve sepet değişmez

#### Scenario: Kendi ürününü satın alma
- **WHEN** satıcı kendi ürününü sepete eklemeye çalışır
- **THEN** istek iş kuralı hatasıyla reddedilir

#### Scenario: Stoğu aşan adet
- **WHEN** alıcı stoktan fazla veya 1–99 aralığı dışında bir adet girer
- **THEN** istek adet alanına bağlı doğrulama hatasıyla reddedilir

#### Scenario: Sepetteki ürün satın alınamaz hale gelir
- **WHEN** sepetteki bir ürün yayından çekilir, tükenir veya satıcısı askıya alınır
- **THEN** satır sepette kalır, satın alınamaz olarak işaretlenir ve toplamlara katılmaz

#### Scenario: Güncel fiyat
- **WHEN** satıcı sepetteki bir ürünün fiyatını değiştirir
- **THEN** sepet bir sonraki okumada yeni fiyatla döner

### Requirement: Kargo ücreti satıcı başınadır ve eşik üstünde ücretsizdir

Sistem her satıcı grubu için kargo ücretini SHALL hesaplar. Grubun ürün tutarı yapılandırılmış eşiğe (1000 TL) eşit veya üzerindeyse kargo ücreti sıfırdır. Değilse yapılandırılmış sabit ücret (99 TL) uygulanır. Kargo ücreti başka satıcıların ürünlerinden etkilenmez.

#### Scenario: Eşiğin altında
- **WHEN** bir satıcı grubunun ürün tutarı 999,99 TL'dir
- **THEN** o grubun kargo ücreti 99 TL'dir ve ücretsiz kargoya 0,01 TL kaldığı gösterilir

#### Scenario: Eşikte
- **WHEN** bir satıcı grubunun ürün tutarı tam 1000 TL'dir
- **THEN** o grubun kargo ücreti sıfırdır

#### Scenario: İki satıcı
- **WHEN** sepette biri eşiğin üstünde, biri altında iki satıcının ürünü vardır
- **THEN** yalnızca eşiğin altındaki satıcının grubu için kargo ücreti alınır

### Requirement: Teslimat adresi yalnızca teslimat yapılan illerden seçilir

Sistem, hesabın en çok 10 teslimat adresi kaydetmesini, güncellemesini ve silmesini SHALL sağlar. Adreste şu alanlar bulunur:

- başlık;
- alıcı adı;
- telefon;
- il;
- ilçe;
- açık adres;
- isteğe bağlı posta kodu.

İl yalnızca yapılandırmadaki teslimat illerinden seçilebilir. Bu iller herkese açık bir uçtan listelenir. Adresi yalnızca sahibi görür ve değiştirir.

#### Scenario: Teslimat yapılan ilde adres
- **WHEN** alıcı teslimat illerinden birinde kurallara uyan bir adres kaydeder
- **THEN** adres kaydedilir ve siparişte seçilebilir

#### Scenario: Teslimat yapılmayan il
- **WHEN** alıcı teslimat listesinde olmayan bir ilde adres kaydetmeye çalışır
- **THEN** istek il alanına bağlı doğrulama hatasıyla reddedilir

#### Scenario: Başkasının adresi
- **WHEN** bir hesap başka bir hesabın adresini okumaya, değiştirmeye, silmeye veya siparişte kullanmaya çalışır
- **THEN** istek 404 ile reddedilir

#### Scenario: Adres sınırı
- **WHEN** 10 adresi olan hesap yeni bir adres ekler
- **THEN** istek iş kuralı hatasıyla reddedilir

#### Scenario: Teslimat listesinden çıkan il
- **WHEN** kayıtlı bir adresin ili teslimat listesinden çıkarılır
- **THEN** adres kayıtlı kalır, teslimat yapılamaz olarak işaretlenir ve siparişte kullanılamaz

### Requirement: Sipariş sepetten oluşturulur, satıcı siparişlerine bölünür ve tutarlar o anda sabitlenir

Sistem, e-posta adresi doğrulanmış alıcının sepetindeki ürünlerden ve seçilen adresten bir sipariş SHALL oluşturur. Sipariş, sepetteki her satıcı için bir satıcı siparişine bölünür. Her satıcı siparişine o anki değerler yazılır:

- birim fiyatlar ve satır tutarları;
- ürün tutarı ve kargo ücreti;
- komisyon oranı ve sabit ücreti, hesaplanan komisyon;
- satıcı hakedişi;
- kargoya verme süresi.

Komisyon `min(ürün tutarı, yuvarla(ürün tutarı × %5) + 3 TL)` olarak hesaplanır; yuvarlama kuruşa ve yarım yukarıdır. Satıcı hakedişi ürün tutarından komisyonun düşülmesiyle bulunur.

Teslimat adresi siparişe kopyalanır. Sipariş oluşturma `Idempotency-Key` başlığı ister; aynı anahtarla tekrarlanan istek yeni sipariş açmaz. E-posta doğrulaması yalnızca sipariş oluşturmanın koşuludur: giriş, sepet, adresler, ödemenin tamamlanması ve siparişleri görme doğrulamaya bağlı değildir.

#### Scenario: Başarılı sipariş oluşturma
- **WHEN** e-postası doğrulanmış alıcı yalnızca satın alınabilir ürünler içeren sepetiyle ve teslimat yapılan bir adresle sipariş oluşturur
- **THEN** sipariş ödeme bekliyor durumunda oluşur, yanıt sipariş numarasını ve ödeme adresini döner ve stok henüz düşmez

#### Scenario: E-postası doğrulanmamış alıcı
- **WHEN** e-postası doğrulanmamış alıcı sipariş oluşturmaya çalışır
- **THEN** istek e-posta doğrulamasını isteyen bir iş kuralı hatasıyla reddedilir, sipariş oluşmaz ve ödeme başlatılmaz

#### Scenario: Doğruladıktan hemen sonra sipariş
- **WHEN** alıcı e-postasını doğrular ve aynı erişim token'ıyla hemen sipariş oluşturur
- **THEN** doğrulama durumu güncel kayıttan okunur ve sipariş oluşur

#### Scenario: Doğrulama kapısı ödeme sayfasında anlatılır
- **WHEN** e-postası doğrulanmamış alıcı ödeme sayfasını açar
- **THEN** sipariş özeti ve toplamlar görünür, ödeme formu yerine e-postayı doğrulama açıklaması ve yeni doğrulama bağlantısı isteme eylemi gösterilir

#### Scenario: Komisyon hesabı
- **WHEN** bir satıcı siparişinin ürün tutarı 245,50 TL'dir
- **THEN** komisyon 12,28 TL + 3 TL = 15,28 TL, satıcı hakedişi 230,22 TL olarak siparişe yazılır

#### Scenario: Komisyon ürün tutarını aşamaz
- **WHEN** bir satıcı siparişinin ürün tutarı 2 TL'dir
- **THEN** komisyon 2 TL olarak yazılır ve satıcı hakedişi sıfır olur, negatif olmaz

#### Scenario: Aynı anahtarla tekrar
- **WHEN** alıcı aynı `Idempotency-Key` ile sipariş oluşturmayı tekrarlar
- **THEN** yeni sipariş oluşmaz ve ilk sipariş döner

#### Scenario: Satın alınamayan satır veya yetersiz stok
- **WHEN** sepette satın alınamaz bir satır vardır ya da bir satırın adedi güncel stoğu aşar
- **THEN** istek iş kuralı hatasıyla reddedilir ve sipariş oluşmaz

#### Scenario: Teslimat yapılamayan veya başkasının adresi
- **WHEN** seçilen adres teslimat yapılamaz durumdadır ya da başka bir hesaba aittir
- **THEN** istek reddedilir ve sipariş oluşmaz

#### Scenario: Sonradan fiyat değişikliği
- **WHEN** sipariş oluştuktan sonra satıcı ürün fiyatını veya yapılandırmadaki komisyon oranı değişir
- **THEN** mevcut siparişin tutarları değişmez

### Requirement: Ödeme sağlayıcıdan doğrulanır; stok ödeme başarılı olunca düşer

Sistem ödemeyi bölünmüş ödeme olarak, her satıcı payını ve pazaryeri payını (komisyon + kargo ücreti) ayırarak SHALL başlatır. Ödemenin sonucunu istemciden değil sağlayıcıdan doğrular.

Başarılı ödemede tek transaction'da şunlar olur:

- ürünlerin stoğu kilitlenerek düşer;
- sipariş ödendi olur, satıcı siparişleri yeni sipariş olur;
- satın alınan ürünler sepetten silinir.

Stok yetmezse ödemenin tamamı iade edilir ve sipariş stok yetersizliğiyle iptal olur. Ödeme yapılandırılmış süre (30 dakika) içinde tamamlanmazsa sipariş süresi dolmuş olur. Tamamlama işlemi tekrar çağrılabilir; sonuçlanmış siparişte mevcut sonuç döner.

Alıcının ödeme dönüşü hiç gelmezse, ödeme süresi yapılandırılmış bir tolerans kadar önce dolmuş siparişleri zamanlanmış bir iş aynı kuralla kapatır: sağlayıcıdan doğrular, alınmış bir ödeme varsa tamamını iade eder ve siparişi sonuçlandırır. İş siparişi kilitleyip durumunu yeniden okur; sipariş silinmez. Sağlayıcı hatası veren bir sipariş, aynı çalışmada ödeme süresi dolmuş diğer siparişlerin işlenmesini engellemez; bir çalışmada her sipariş en çok bir kez denenir.

#### Scenario: Başarılı ödeme
- **WHEN** sağlayıcı ödemenin başarılı olduğunu doğrular ve stok yeterlidir
- **THEN** stok düşer, sipariş ödendi ve satıcı siparişleri yeni durumuna geçer, ürünler sepetten silinir, alıcıya ve her satıcıya e-posta gider

#### Scenario: Son ürünü iki alıcı öder
- **WHEN** stoğu 1 olan bir ürünü içeren iki siparişin ödemesi başarılı olur
- **THEN** ilk tamamlanan sipariş ödenir, ikincinin ödemesi tamamen iade edilir ve ikinci sipariş stok yetersizliğiyle iptal olur; stok negatife düşmez

#### Scenario: Başarısız ödeme
- **WHEN** sağlayıcı ödemenin başarısız olduğunu bildirir
- **THEN** sipariş ödeme başarısız durumuna geçer, stok ve sepet değişmez

#### Scenario: Süresi dolan ödeme
- **WHEN** ödeme tamamlama isteği sipariş oluştuktan 30 dakika sonra gelir
- **THEN** sipariş süresi dolmuş olur, stok ve sepet değişmez

#### Scenario: Dönüşü gelmeyen ödenmemiş sipariş işle kapanır
- **WHEN** sipariş ödeme bekliyor durumundadır, ödeme süresi toleranstan daha önce dolmuştur ve sağlayıcı ödemeyi beklemede ya da başarısız bildirir
- **THEN** iş siparişi süresi dolmuş ya da ödeme başarısız durumuna geçirir, satıcı siparişleri kapanır, geçmişe yapan taraf sistem olarak yazılır; stok, sepet ve para değişmez

#### Scenario: Geç alınmış ödeme işte iade edilir
- **WHEN** iş, ödeme süresi dolmuş bir sipariş için sağlayıcıdan ödemenin başarılı olduğunu öğrenir
- **THEN** ödemenin tamamı iade edilir ve sipariş süresi dolmuş durumuna geçer; stok düşmez

#### Scenario: Tolerans dolmamış sipariş beklenir
- **WHEN** siparişin ödeme süresi dolmuştur ama tolerans henüz geçmemiştir
- **THEN** iş siparişe dokunmaz; alıcının ödeme dönüşü önceliklidir

#### Scenario: Sağlayıcıya ulaşılamaz
- **WHEN** iş bir siparişi işlerken sağlayıcı hata verir
- **THEN** o sipariş değişmez ve sonraki turda yeniden denenir; diğer siparişler işlenir

#### Scenario: Hatalı siparişler arkadakileri bekletmez
- **WHEN** ödeme süresi dolmuş siparişlerin en eskileri, bir çalışmanın bir seferde okuduğu sayıdan fazla olacak kadar çok ve sağlayıcı hepsinde hata verir
- **THEN** aynı çalışmada arkadaki siparişler de işlenir, hatalı siparişler değişmeden kalır ve her biri bu çalışmada yalnızca bir kez denenir

#### Scenario: Başkasının siparişi
- **WHEN** bir hesap başka bir hesabın siparişinin ödemesini tamamlamaya çalışır
- **THEN** istek 404 ile reddedilir

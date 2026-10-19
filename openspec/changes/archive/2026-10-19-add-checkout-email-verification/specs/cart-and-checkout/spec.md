# cart-and-checkout Specification (delta)

> Bu bir **delta spec**'tir: bu change'in getirdiği davranışı tanımlar. Uygulanıp onaylandıktan sonra `openspec/specs/cart-and-checkout/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar: `design.md` D1–D2.

## MODIFIED Requirements

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

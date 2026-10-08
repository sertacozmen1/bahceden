# orders Specification

## Purpose
Alıcının ve satıcının siparişleri görmesini, kargoya verme son tarihini, kargodan önce iptal ve tam iadeyi, durum geçmişini ve sipariş e-postalarını tanımlar. Kararlar: ADR-056, ADR-058, ADR-064, ADR-066, ADR-067. Kargo durumları ve ayıplı ürün iadesi bu capability'nin bugünkü kapsamı dışındadır.

## Requirements

### Requirement: Alıcı kendi siparişlerini görür

Sistem, alıcının kendi siparişlerini en yeniden eskiye, sayfalı olarak listelemesini SHALL sağlar. Alıcı her siparişin detayını da görür. Detay şunları içerir:

- sipariş numarası ve durumu;
- teslimat adresi;
- satıcılara göre bölünmüş satırlar: ürün adı, adet, birim fiyat, satır tutarı;
- her satıcı siparişinin kargo ücreti ve durumu;
- kargoya verme son tarihi;
- genel toplam.

Komisyon ve satıcı hakedişi alıcıya gösterilmez.

#### Scenario: Siparişlerim
- **WHEN** alıcı sipariş listesini ister
- **THEN** yalnızca kendi siparişleri ortak liste şeklinde döner

#### Scenario: Başkasının siparişi
- **WHEN** alıcı başka bir hesabın sipariş numarasıyla detay ister
- **THEN** 404 döner ve siparişin varlığı açığa çıkmaz

#### Scenario: Oturum olmadan
- **WHEN** kimlik bilgisi olmadan sipariş listesi veya detayı istenir
- **THEN** istek 401 ile reddedilir

### Requirement: Satıcı kendi satıcı siparişlerini görür

Sistem, onaylı satıcının yalnızca kendisine ait satıcı siparişlerini en yeniden eskiye, sayfalı ve durumla süzülebilir biçimde listelemesini SHALL sağlar. Satıcı her satıcı siparişinin detayını da görür. Detay şunları içerir:

- satırlar;
- ürün tutarı;
- komisyon ve satıcı hakedişi;
- kargoya verme son tarihi;
- alıcı adresinden yalnızca il ve ilçe.

Ödemesi tamamlanmamış satıcı siparişleri satıcıya gösterilmez. Her işlem önce onaylı satıcı kapısından, sonra sorgu seviyesindeki sahiplik denetiminden geçer.

#### Scenario: Gelen siparişler
- **WHEN** onaylı satıcı sipariş listesini ister
- **THEN** yalnızca kendi ödenmiş satıcı siparişleri döner; ödeme bekleyen veya ödemesi başarısız siparişler görünmez

#### Scenario: Alıcı adresi gizliliği
- **WHEN** satıcı bir satıcı siparişinin detayını ister
- **THEN** yanıtta alıcının il ve ilçesi bulunur; adı, telefonu ve açık adresi bulunmaz

#### Scenario: Başka satıcının siparişi
- **WHEN** satıcı başka bir satıcının satıcı siparişini ister veya iptal etmeye çalışır
- **THEN** istek 404 ile reddedilir

#### Scenario: Onaylı olmayan satıcı
- **WHEN** askıdaki veya onaylı olmayan bir satıcı sipariş uçlarından birini çağırır
- **THEN** istek 403 ile reddedilir

### Requirement: Kargoya verilmemiş satıcı siparişi iptal edilir ve tamamı iade edilir

Sistem, `NEW` durumundaki bir satıcı siparişinin alıcı tarafından, isteğe bağlı bir gerekçeyle iptal edilmesini SHALL sağlar. Satıcı da aynı siparişi zorunlu bir gerekçeyle iptal edebilir.

İptal tek transaction'da yapılır ve şunları içerir:

- satıcı siparişi iptal edilir;
- ürünlerin stoğu geri eklenir;
- komisyon ve hakediş geçersiz olur;
- satıcı siparişinin ürün tutarı ile kargo ücretinin toplamı ödeme sağlayıcısından iade edilir.

İade başarısız olursa iptal gerçekleşmez. İptal, karşı tarafa hesabının dilinde e-postayla bildirilir. E-posta gönderilemezse iptal yine geçerlidir.

#### Scenario: Alıcı iptal eder
- **WHEN** alıcı `NEW` durumundaki bir satıcı siparişini iptal eder
- **THEN** satıcı siparişi iptal edilir, ürün tutarı ve kargo ücreti iade edilir, stok geri eklenir ve satıcıya e-posta gider

#### Scenario: Satıcı gerekçeyle iptal eder
- **WHEN** satıcı `NEW` durumundaki satıcı siparişini gerekçe yazarak iptal eder
- **THEN** satıcı siparişi iptal edilir, tam iade yapılır, stok geri eklenir ve alıcıya gerekçeyi içeren e-posta gider

#### Scenario: Gerekçesiz satıcı iptali
- **WHEN** satıcı gerekçe olmadan iptal ister
- **THEN** istek doğrulama hatasıyla reddedilir ve sipariş değişmez

#### Scenario: Diğer satıcıların siparişi etkilenmez
- **WHEN** iki satıcılı bir siparişte yalnızca bir satıcı siparişi iptal edilir
- **THEN** diğer satıcı siparişi ve onun ödemesi değişmez

#### Scenario: İptal edilemeyen durum
- **WHEN** zaten iptal edilmiş veya ödemesi tamamlanmamış bir satıcı siparişi iptal edilmek istenir
- **THEN** istek çakışma hatasıyla reddedilir

#### Scenario: İade başarısız olur
- **WHEN** ödeme sağlayıcısı iadeyi reddeder
- **THEN** satıcı siparişi değişmez, stok geri eklenmez ve istek sağlayıcı hatası döner

### Requirement: Kargoya verme son tarihi gösterilir

Sistem, ödeme anında her satıcı siparişi için kargoya verme son tarihini SHALL hesaplar. Süre, satıcı siparişindeki ürünlerin beyan ettiği en uzun süredir. Bu süre Türkiye saatiyle iş günü olarak sayılır; cumartesi ve pazar atlanır. Son tarih alıcıya ve satıcıya gösterilir. Son tarih geçmiş ve sipariş hâlâ kargoya verilmemişse bu durum iki tarafa da bilgi olarak gösterilir.

#### Scenario: Cuma ödemesi
- **WHEN** kargoya verme süresi 2 iş günü olan bir sipariş cuma günü ödenir
- **THEN** son tarih bir sonraki salı günü 23:59 (Türkiye saati) olur

#### Scenario: Süre aşımı
- **WHEN** son tarih geçmiştir ve satıcı siparişi `NEW` durumundadır
- **THEN** alıcı ve satıcı ekranında süre aşımı bilgisi görünür ve alıcı siparişi iptal edebilir

### Requirement: Satıcı siparişi geçmişi saklanır ve tarafların e-postası gider

Sistem her satıcı siparişi durum geçişini silinmeyen bir kayıt olarak SHALL saklar. Kayıtta şunlar bulunur: önceki ve yeni durum, yapan taraf, zaman ve varsa gerekçe.

Ödeme başarılı olduğunda iki e-posta gider:

- alıcıya sipariş özetiyle bir onay e-postası;
- her satıcıya kendi satıcı siparişinin özetiyle yeni sipariş e-postası.

E-postalar alıcının ve satıcının hesap dilindedir. Teslimat adresi ve sipariş içeriği uygulama log'una yazılmaz.

#### Scenario: Sipariş onayı e-postası
- **WHEN** İngilizce dilli bir alıcının ödemesi başarılı olur
- **THEN** alıcıya İngilizce bir sipariş onayı e-postası gider ve Türkçe dilli satıcıya Türkçe yeni sipariş e-postası gider

#### Scenario: Geçmiş tutulur
- **WHEN** bir satıcı siparişi oluşur, ödenir ve alıcı tarafından iptal edilir
- **THEN** bu geçişler yapan taraf ve zamanla birlikte sırayla kayıtlıdır

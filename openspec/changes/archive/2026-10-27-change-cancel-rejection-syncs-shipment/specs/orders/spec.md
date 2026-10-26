## MODIFIED Requirements

### Requirement: Kargoya verilmemiş satıcı siparişi iptal edilir ve tamamı iade edilir

Sistem, `NEW` durumundaki bir satıcı siparişinin alıcı tarafından, isteğe bağlı bir gerekçeyle iptal edilmesini SHALL sağlar. Satıcı da aynı siparişi zorunlu bir gerekçeyle iptal edebilir. Kargoya verilmiş (`SHIPPED` ve sonrası) satıcı siparişi iptal edilemez.

İptal tek transaction'da yapılır ve şunları içerir:

- gönderi açılmışsa gönderi önce entegratörde iptal edilir ve `CANCELLED` olur;
- satıcı siparişi iptal edilir;
- ürünlerin stoğu geri eklenir;
- komisyon ve hakediş geçersiz olur;
- satıcı siparişinin ürün tutarı ile kargo ücretinin toplamı ödeme sağlayıcısından iade edilir.

Entegratör gönderi iptalini reddederse veya iade başarısız olursa iptal gerçekleşmez. Entegratör gönderi iptalini reddederse sistem, iptal geri alındıktan sonra gönderinin durumunu hemen entegratörden okur ve durum sorgusunun kurallarıyla uygular. Paket kargoya verilmiş çıkarsa istek `ORDER_ALREADY_SHIPPED` çakışma hatasıyla reddedilir; değilse veya durum okunamazsa entegratör hatası döner. İptal, karşı tarafa hesabının dilinde e-postayla bildirilir. E-posta gönderilemezse iptal yine geçerlidir.

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
- **WHEN** zaten iptal edilmiş, ödemesi tamamlanmamış veya kargoya verilmiş bir satıcı siparişi iptal edilmek istenir
- **THEN** istek çakışma hatasıyla reddedilir

#### Scenario: İade başarısız olur
- **WHEN** ödeme sağlayıcısı iadeyi reddeder
- **THEN** satıcı siparişi değişmez, stok geri eklenmez ve istek sağlayıcı hatası döner

#### Scenario: Gönderisi açılmış sipariş iptal edilir
- **WHEN** gönderisi `CREATED` durumunda olan bir satıcı siparişi iptal edilir
- **THEN** gönderi entegratörde iptal edilir ve `CANCELLED` olur, ardından tam iade yapılır

#### Scenario: Entegratör gönderi iptalini reddeder
- **WHEN** entegratör gönderi iptalini reddeder ve gönderinin entegratördeki durumu hâlâ kargoya verilmemiştir veya okunamaz
- **THEN** satıcı siparişi ve gönderi değişmez, iade yapılmaz ve istek entegratör hatası döner

#### Scenario: Paket durum sorgusundan önce kargoya verilmiş
- **WHEN** gönderisi sistemde `CREATED` görünen ama entegratörde kargoya verilmiş bir satıcı siparişi iptal edilmek istenir
- **THEN** iptal ve iade yapılmaz, stok değişmez; gönderi ve satıcı siparişi kargoya verildi durumuna geçer, alıcıya kargo e-postası gider ve istek `ORDER_ALREADY_SHIPPED` çakışma hatasıyla reddedilir

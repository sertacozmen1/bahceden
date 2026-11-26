## MODIFIED Requirements

### Requirement: Ödeme sağlayıcıdan doğrulanır; stok ödeme başarılı olunca düşer

Sistem ödemeyi bölünmüş ödeme olarak, her satıcı payını ve pazaryeri payını (komisyon + kargo ücreti) ayırarak SHALL başlatır. Ödemenin sonucunu istemciden değil sağlayıcıdan doğrular.

Başarılı ödemede tek transaction'da şunlar olur:

- ürünlerin stoğu kilitlenerek düşer;
- sipariş ödendi olur, satıcı siparişleri yeni sipariş olur;
- satın alınan ürünler sepetten silinir;
- bir "sipariş ödendi" olayı kalıcı olarak kaydedilir.

Sipariş ödendi olayı kayıttan sonra iş olayları akışına yayınlanır. Olay yalnızca kimlikleri ve toplam tutarı taşır; ad, e-posta, adres veya telefon taşımaz. Alıcıya ve her satıcıya giden e-postalar bu olay işlenince, alıcının ve satıcının hesap dilinde gönderilir. Olay akışına geçici olarak ulaşılamazsa sipariş etkilenmez; olay bekler ve akış döndüğünde yayınlanır. Aynı olay birden fazla teslim edilse de e-postalar bir kez gönderilir.

Stok yetmezse ödemenin tamamı iade edilir ve sipariş stok yetersizliğiyle iptal olur. Ödeme yapılandırılmış süre (30 dakika) içinde tamamlanmazsa sipariş süresi dolmuş olur. Tamamlama işlemi tekrar çağrılabilir; sonuçlanmış siparişte mevcut sonuç döner.

Alıcının ödeme dönüşü hiç gelmezse, ödeme süresi yapılandırılmış bir tolerans kadar önce dolmuş siparişleri zamanlanmış bir iş aynı kuralla kapatır: sağlayıcıdan doğrular, alınmış bir ödeme varsa tamamını iade eder ve siparişi sonuçlandırır. İş siparişi kilitleyip durumunu yeniden okur; sipariş silinmez. Sağlayıcı hatası veren bir sipariş, aynı çalışmada ödeme süresi dolmuş diğer siparişlerin işlenmesini engellemez; bir çalışmada her sipariş en çok bir kez denenir.

#### Scenario: Başarılı ödeme
- **WHEN** sağlayıcı ödemenin başarılı olduğunu doğrular ve stok yeterlidir
- **THEN** stok düşer, sipariş ödendi ve satıcı siparişleri yeni durumuna geçer, ürünler sepetten silinir, bir sipariş ödendi olayı kaydedilir; olay işlenince alıcıya ve her satıcıya e-posta gider

#### Scenario: Olay akışına ulaşılamıyor
- **WHEN** ödeme başarılı olur ama olay akışı o anda erişilemezdir
- **THEN** sipariş yine ödendi olur; olay bekler, akış erişilebilir olunca yayınlanır ve e-postalar o zaman gider

#### Scenario: Olay iki kez teslim edilir
- **WHEN** aynı sipariş ödendi olayı tüketiciye ikinci kez ulaşır
- **THEN** ikinci teslim yok sayılır; alıcıya ve satıcılara ikinci e-posta gitmez

#### Scenario: Sonuçlanmayan ödeme olay üretmez
- **WHEN** ödeme başarısız olur, süresi dolar veya stok yetmediği için iade edilir
- **THEN** sipariş ödendi olayı kaydedilmez

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

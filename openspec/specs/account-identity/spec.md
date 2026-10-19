# account-identity Specification

## Purpose
Hesap açma, e-posta doğrulama (girişi engellemeyen), oturum açma, hesap bazlı anahtarla e-posta ikinci faktörü, oturum yenileme ve sonlandırma, e-postayla parola sıfırlama, oturum açıkken parola değiştirme ile oturum sahibinin kimliğini tanımlar. Token'lar tarayıcı JavaScript'ine verilmez. E-posta adresini değiştirme ve hesap silme bu capability'nin bugünkü kapsamı dışındadır; satıcı olma `seller-onboarding` capability'sindedir.

## Requirements

### Requirement: E-posta ve parola ile hesap açma

Sistem, e-posta ve parola ile hesap açmayı SHALL sağlar. Hesap doğrudan etkin durumda açılır ve kullanıcıya `ROLE_CUSTOMER` rolü verilir. Kayıt yanıtı, girilen e-postanın sistemde kayıtlı olup olmadığını **açığa çıkarmaz**: her durumda aynı durum kodu ve aynı gövde döner.

#### Scenario: Yeni e-posta ile kayıt
- **WHEN** kullanıcı sistemde bulunmayan bir e-posta ve geçerli bir parola ile kayıt olur
- **THEN** etkin bir hesap açılır, e-posta adresine tek kullanımlık ve süreli bir doğrulama bağlantısı gönderilir ve genel bir "bağlantı gönderildi" yanıtı döner

#### Scenario: Kayıt sonrası giriş e-postaya bağlı değildir
- **WHEN** kullanıcı kayıt olduktan hemen sonra, doğrulama bağlantısını hiç kullanmadan giriş yapar
- **THEN** oturum açılır; doğrulama bağlantısının kullanılmamış olması girişi engellemez

#### Scenario: Var olan bir hesabın e-postasıyla kayıt
- **WHEN** kullanıcı sistemde kayıtlı bir e-posta ile kayıt olmayı dener
- **THEN** ikinci bir hesap açılmaz, mevcut hesabın sahibine durumu bildiren bir e-posta gönderilir ve yeni kayıtla **aynı** yanıt döner

#### Scenario: Geçersiz kayıt bilgisi
- **WHEN** kullanıcı geçersiz biçimde bir e-posta veya kuralları karşılamayan bir parola gönderir
- **THEN** istek doğrulama hatasıyla reddedilir ve ortak hata gövdesinde hatalı alanlar bildirilir

#### Scenario: Parola asla geri okunamaz
- **WHEN** bir hesap açılır
- **THEN** parola yalnızca hash'lenmiş olarak saklanır; hiçbir uç, log kaydı veya hata mesajı parolayı düz metin olarak içermez

#### Scenario: E-posta gönderilemezse kayıt yine tamamlanır
- **WHEN** kayıt sırasında doğrulama e-postası gönderilemez
- **THEN** hesap yine açılır, kullanıcı diğer durumlarla aynı yanıtı alır ve gönderim hatası sunucu kaydına yazılır

### Requirement: E-posta doğrulama (girişi engellemeyen)

Sistem, e-posta adresinin doğrulanmasını SHALL sağlar. Doğrulama token'ı tek kullanımlıktır, süre sınırlıdır ve veritabanında yalnızca hash'i saklanır. Doğrulanmış olmak bir **bilgidir**, giriş koşulu değildir.

#### Scenario: Geçerli token ile doğrulama
- **WHEN** kullanıcı süresi geçmemiş ve daha önce kullanılmamış bir doğrulama token'ı gönderir
- **THEN** hesap doğrulanmış olarak işaretlenir ve token tüketilir

#### Scenario: Token iki kez kullanılamaz
- **WHEN** kullanıcı daha önce kullanılmış bir doğrulama token'ını yeniden gönderir
- **THEN** istek reddedilir ve hesabın doğrulama durumu değişmez

#### Scenario: Süresi dolmuş token
- **WHEN** kullanıcı süresi dolmuş bir doğrulama token'ı gönderir
- **THEN** istek reddedilir ve kullanıcıya yeni bir bağlantı isteyebileceği bildirilir

#### Scenario: Yeni doğrulama bağlantısı talebi
- **WHEN** e-postası doğrulanmamış, oturum açmış bir kullanıcı yeni bir doğrulama bağlantısı ister
- **THEN** kendi e-posta adresine yeni, tek kullanımlık ve süreli bir bağlantı gönderilir

#### Scenario: Doğrulanmış hesap için bağlantı talebi
- **WHEN** e-postası zaten doğrulanmış bir kullanıcı yeni bağlantı ister
- **THEN** istek kabul edilir ama e-posta gönderilmez

#### Scenario: Oturumsuz bağlantı talebi
- **WHEN** oturum açmamış biri yeni doğrulama bağlantısı ister
- **THEN** istek kimlik doğrulama hatasıyla reddedilir; bir e-posta adresi verilerek bağlantı istenemez

#### Scenario: Doğrulama durumu kullanıcıya görünür
- **WHEN** oturum açmış kullanıcı kendi kimlik bilgisini talep eder
- **THEN** yanıt, e-postasının doğrulanmış olup olmadığını içerir

### Requirement: Oturum açma

Sistem, e-posta ve parola ile oturum açmayı SHALL sağlar. Oturum, kısa ömürlü bir erişim token'ı ve iptal edilebilir bir yenileme token'ı ile temsil edilir. Kimlik bilgileri hatalı olduğunda hangi bilginin hatalı olduğunu açığa çıkarmayan tek ve genel bir hata döner.

#### Scenario: Geçerli kimlik bilgileri
- **WHEN** kullanıcı doğru e-posta ve parolasını gönderir ve hesabında ikinci faktör kapalıdır
- **THEN** kısa ömürlü bir erişim token'ı ve bir yenileme token'ı döner

#### Scenario: Hatalı parola ile hatalı e-posta aynı yanıtı döner
- **WHEN** kullanıcı var olmayan bir e-posta ile ya da var olan bir hesabın hatalı parolasıyla giriş yapmaya çalışır
- **THEN** iki durumda da **aynı** hata kodu ve aynı mesaj döner; hesabın var olup olmadığı anlaşılmaz

#### Scenario: Hız sınırı
- **WHEN** aynı kaynaktan kısa sürede izin verilenden fazla giriş denemesi yapılır
- **THEN** sonraki istekler hız sınırı hatasıyla reddedilir ve pencere dolduğunda yeniden kabul edilir

### Requirement: E-posta ile ikinci faktör, hesap bazlı anahtarla

Hesap üzerinde ikinci faktör anahtarı SHALL bulunur. Anahtar kapalıyken giriş tek adımlıdır. Anahtar açıkken giriş iki adımlıdır: birinci adım token döndürmez, e-posta ile tek kullanımlık bir kod gönderir; token'lar ancak bu kod doğrulandığında verilir. Anahtarı değiştiren bir uç bulunmaz; varsayılan değeri kapalıdır.

#### Scenario: Anahtar kapalı (varsayılan)
- **WHEN** ikinci faktörü kapalı bir hesapla doğru kimlik bilgileriyle giriş yapılır
- **THEN** token'lar tek adımda döner ve e-posta gönderilmez

#### Scenario: Anahtar açık, birinci adım
- **WHEN** ikinci faktörü açık bir hesapla doğru kimlik bilgileriyle giriş yapılır
- **THEN** token döndürülmez; ikinci adımın beklendiği bildirilir ve e-posta ile tek kullanımlık bir kod gönderilir

#### Scenario: Anahtar açık, geçerli kod
- **WHEN** kullanıcı birinci adımın ardından gönderilen geçerli kodu sunar
- **THEN** erişim ve yenileme token'ları döner ve kod tüketilir

#### Scenario: Kod iki kez kullanılamaz
- **WHEN** kullanıcı daha önce kullanılmış bir ikinci faktör kodunu yeniden sunar
- **THEN** istek reddedilir ve oturum açılmaz

#### Scenario: Süresi dolmuş kod
- **WHEN** kullanıcı süresi dolmuş bir ikinci faktör kodu sunar
- **THEN** istek reddedilir ve kullanıcı yeniden giriş yapmaya yönlendirilir

#### Scenario: Kod denemeleri sınırlıdır
- **WHEN** aynı oturum girişimi için izin verilenden fazla hatalı kod denemesi yapılır
- **THEN** sonraki denemeler hız sınırı hatasıyla reddedilir

### Requirement: Oturum yenileme ve sonlandırma

Yenileme token'ı tek kullanımlıktır ve her kullanımda yenisiyle değiştirilir. Kullanılmış bir yenileme token'ı, kullanıldığı andan itibaren kısa bir tolerans penceresi (30 saniye) içinde yeniden sunulursa aynı oturum zincirinde yeni token'lar verilir; pencere dışında sunulması sızıntı işareti sayılır ve o oturum zincirinin tamamı geçersiz kılınır. Kullanıcı oturumunu sonlandırdığında yenileme token'ı geçersiz olur.

#### Scenario: Yenileme ile yeni erişim token'ı
- **WHEN** kullanıcı geçerli bir yenileme token'ı sunar
- **THEN** yeni bir erişim token'ı ve yeni bir yenileme token'ı döner; sunulan token bir daha kullanılamaz

#### Scenario: Tolerans penceresinde tekrar sunulan yenileme token'ı
- **WHEN** kullanılmış bir yenileme token'ı, kullanıldığı andan itibaren 30 saniye içinde yeniden sunulur
- **THEN** aynı oturum zincirinde yeni bir erişim token'ı ve yeni bir yenileme token'ı döner; daha önce verilen token'lar geçerli kalır

#### Scenario: Tolerans penceresi tekrar kullanımla uzamaz
- **WHEN** tolerans penceresinde yeniden kullanılmış bir token, ilk kullanımından 30 saniye sonra bir kez daha sunulur
- **THEN** istek reddedilir ve oturum zincirinin tamamı geçersiz kılınır

#### Scenario: Kullanılmış yenileme token'ının pencere dışında tekrar sunulması
- **WHEN** daha önce kullanılmış bir yenileme token'ı, tolerans penceresi geçtikten sonra yeniden sunulur
- **THEN** istek reddedilir ve aynı oturum zincirine ait tüm yenileme token'ları geçersiz kılınır; kullanıcı yeniden giriş yapmak zorundadır

#### Scenario: Oturumu sonlandırma
- **WHEN** kullanıcı oturumunu sonlandırır
- **THEN** yenileme token'ı geçersiz olur ve sonraki yenileme denemeleri reddedilir

#### Scenario: Oturum sonlandırıldıktan sonra tolerans yoktur
- **WHEN** oturum sonlandırıldıktan sonra zincirin kullanılmış bir token'ı tolerans penceresi içinde sunulur
- **THEN** istek reddedilir

#### Scenario: Süresi dolmuş yenileme token'ı
- **WHEN** kullanıcı süresi dolmuş bir yenileme token'ı sunar
- **THEN** istek reddedilir ve kullanıcı giriş ekranına yönlendirilir

### Requirement: Oturum sahibinin kimliği

Sistem, oturum açmış kullanıcının kimliğini, rollerini ve e-posta doğrulama durumunu bildiren bir uç SHALL sunar. Bu uç kimlik doğrulaması gerektirir ve bilgiyi güncel kaynaktan okur.

#### Scenario: Oturumlu erişim
- **WHEN** geçerli bir erişim token'ı ile kimlik bilgisi talep edilir
- **THEN** hesabın e-postası, rolleri ve e-posta doğrulama durumu döner

#### Scenario: Kimliksiz erişim
- **WHEN** erişim token'ı olmadan ya da geçersiz bir token ile kimlik bilgisi talep edilir
- **THEN** istek kimlik doğrulama hatasıyla reddedilir

#### Scenario: Rol değişikliği güncel yansır
- **WHEN** hesabın rolleri değiştikten sonra kimlik bilgisi talep edilir
- **THEN** dönen roller güncel kaynaktaki hâlini yansıtır

### Requirement: Token'lar tarayıcı JavaScript'ine verilmez

Arayüz, token'ları yalnızca sunucu tarafında taşır. Token'lar tarayıcıda betik erişimine açık hiçbir yerde tutulmaz.

#### Scenario: Giriş sonrası token'ın saklanması
- **WHEN** kullanıcı arayüz üzerinden giriş yapar
- **THEN** token'lar yalnızca betikle okunamayan çerezlerde tutulur; arayüz yanıtı token içermez

#### Scenario: Oturumlu sayfa isteği
- **WHEN** oturumlu bir sayfa sunucu tarafında render edilir
- **THEN** istek kimlik bilgisini sunucu tarafında ekler; tarayıcı tarafında token bulunmaz

#### Scenario: Erişim token'ının süresi dolduğunda
- **WHEN** oturumlu bir sayfa isteği erişim token'ının süresi dolmuş olduğu için reddedilir
- **THEN** oturum bir kez yenilenmeye çalışılır; yenileme de başarısız olursa kullanıcı giriş ekranına yönlendirilir

#### Scenario: Tarayıcı yeniden açıldığında oturum sürer
- **WHEN** oturumu süresi dolmamış bir kullanıcı tarayıcıyı kapatıp açar ve oturum gerektirmeyen herhangi bir sayfayı açar
- **THEN** oturum sessizce yenilenir ve kullanıcı aynı sayfayı oturumu açık olarak görür

#### Scenario: Sessiz yenileme başarısız olduğunda
- **WHEN** oturum gerektirmeyen bir sayfada yapılan sessiz yenileme reddedilir
- **THEN** oturum çerezleri silinir ve kullanıcı giriş ekranına yönlendirilmeden aynı sayfayı oturumsuz görür

#### Scenario: Birden çok sekme aynı anda yenilemek ister
- **WHEN** aynı tarayıcıda birden çok sekme süresi dolmuş oturumla aynı anda açılır
- **THEN** yenilemeyi tek sekme yapar, diğerleri onu bekleyip yenilenmiş oturumla açılır; hiçbir sekme oturumu kapatmaz

#### Scenario: Siteler arası istek
- **WHEN** oturum durumunu değiştiren bir arayüz isteği başka bir siteden tetiklenir
- **THEN** istek reddedilir

### Requirement: Parola sıfırlama

Sistem, parolasını unutan kullanıcının e-posta adresine gönderilen bir bağlantıyla yeni parola belirlemesini SHALL sağlar. Sıfırlama token'ı tek kullanımlıktır, bir saat geçerlidir ve veritabanında yalnızca hash'i saklanır. Talebe verilen yanıt, e-posta adresinin kayıtlı olup olmadığını açığa çıkarmaz.

#### Scenario: Kayıtlı e-posta ile sıfırlama talebi
- **WHEN** biri kayıtlı bir e-posta adresi için parola sıfırlama ister
- **THEN** istek kabul edilir ve o adrese, hesabın dilinde, tek kullanımlık bir sıfırlama bağlantısı gönderilir

#### Scenario: Kayıtsız e-posta ile sıfırlama talebi
- **WHEN** biri kayıtlı olmayan bir e-posta adresi için parola sıfırlama ister
- **THEN** istek kayıtlı adresteki ile aynı yanıtla kabul edilir ve hiçbir e-posta gönderilmez

#### Scenario: Yeni talep eski bağlantıları geçersiz kılar
- **WHEN** kullanıcı ikinci kez sıfırlama ister ve ilk bağlantıyı kullanmaya çalışır
- **THEN** ilk bağlantı reddedilir; yalnızca en son gönderilen bağlantı geçerlidir

#### Scenario: Geçerli bağlantı ile yeni parola
- **WHEN** kullanıcı geçerli bir sıfırlama token'ı ve kurallara uyan yeni bir parola gönderir
- **THEN** parola değişir, token tüketilir, eski parola ile giriş reddedilir ve yeni parola ile giriş kabul edilir

#### Scenario: Sıfırlama tüm oturumları kapatır
- **WHEN** parolası sıfırlanan hesabın açık oturumları ve süresi geçmemiş giriş kodları vardır
- **THEN** o oturumların yenileme token'ları ve giriş kodları artık kabul edilmez

#### Scenario: Sıfırlama oturum açmaz
- **WHEN** parola başarıyla sıfırlanır
- **THEN** yanıt oturum token'ı içermez; kullanıcı olağan giriş akışından (varsa ikinci faktör dahil) geçer

#### Scenario: Sıfırlama e-postayı doğrular
- **WHEN** e-postası doğrulanmamış bir hesabın parolası sıfırlama bağlantısıyla değiştirilir
- **THEN** hesabın e-postası doğrulanmış olarak işaretlenir

#### Scenario: Parola değişikliği bildirilir
- **WHEN** parola sıfırlama bağlantısıyla değiştirilir
- **THEN** hesabın e-posta adresine, hesabın dilinde, parolanın değiştiğini bildiren bir e-posta gönderilir

#### Scenario: Geçersiz, kullanılmış veya süresi dolmuş token
- **WHEN** kullanıcı geçersiz, daha önce kullanılmış veya süresi dolmuş bir sıfırlama token'ı gönderir
- **THEN** istek tek ve aynı hatayla reddedilir ve parola değişmez

#### Scenario: Kurallara uymayan yeni parola
- **WHEN** kullanıcı geçerli bir token ile kayıt kurallarına uymayan bir parola gönderir
- **THEN** istek doğrulama hatasıyla reddedilir, parola değişmez ve token tüketilmez

#### Scenario: Hız sınırı
- **WHEN** aynı istemciden veya aynı e-posta adresi için kısa sürede çok sayıda sıfırlama talebi gelir
- **THEN** sınırı aşan talepler hız sınırı hatasıyla reddedilir ve e-posta gönderilmez

### Requirement: Oturum açıkken parola değiştirme

Sistem, oturum açmış kullanıcının mevcut parolasını girerek yeni bir parola belirlemesini SHALL sağlar. Başarılı değişiklik hesabın, isteği yapan oturum dahil, bütün oturumlarını ve açık giriş kodlarını kapatır; yeni oturum açmaz. Mevcut parola yanlışsa veya yeni parola kurallara uymuyorsa hiçbir şey değişmez.

#### Scenario: Doğru mevcut parola ile değiştirme
- **WHEN** oturum açmış kullanıcı doğru mevcut parolasını ve kayıt kurallarına uyan, mevcut parolasından farklı bir yeni parola gönderir
- **THEN** parola değişir, eski parola ile giriş reddedilir ve yeni parola ile giriş kabul edilir

#### Scenario: Değişiklik mevcut oturum dahil bütün oturumları kapatır
- **WHEN** parolası değişen hesabın, değişikliği yapan oturum dahil birden fazla açık oturumu ve süresi geçmemiş giriş kodu vardır
- **THEN** o oturumların hiçbirinin yenileme token'ı ve hiçbir giriş kodu artık kabul edilmez

#### Scenario: Değişiklik oturum açmaz
- **WHEN** parola başarıyla değişir
- **THEN** yanıt oturum token'ı içermez; kullanıcı olağan giriş akışından (varsa ikinci faktör dahil) geçer

#### Scenario: Parola değişikliği bildirilir
- **WHEN** parola oturum açıkken değiştirilir
- **THEN** hesabın e-posta adresine, hesabın dilinde, parolanın değiştiğini ve değişikliği kullanıcı yapmadıysa nasıl sıfırlayacağını bildiren bir e-posta gönderilir

#### Scenario: E-posta doğrulama durumu değişmez
- **WHEN** e-postası doğrulanmamış bir hesabın parolası oturum açıkken değiştirilir
- **THEN** hesabın e-postası doğrulanmamış olarak kalır

#### Scenario: Yanlış mevcut parola
- **WHEN** oturum açmış kullanıcı yanlış bir mevcut parola gönderir
- **THEN** istek mevcut parola alanına bağlı bir doğrulama hatasıyla reddedilir; parola, oturumlar ve giriş kodları değişmez

#### Scenario: Kurallara uymayan yeni parola
- **WHEN** oturum açmış kullanıcı doğru mevcut parolayla kayıt kurallarına uymayan bir yeni parola gönderir
- **THEN** istek yeni parola alanına bağlı bir doğrulama hatasıyla reddedilir; parola ve oturumlar değişmez

#### Scenario: Yeni parola mevcut parolayla aynı
- **WHEN** oturum açmış kullanıcı yeni parola olarak mevcut parolasını gönderir
- **THEN** istek yeni parola alanına bağlı bir doğrulama hatasıyla reddedilir; oturumlar kapanmaz

#### Scenario: Oturumsuz istek
- **WHEN** erişim token'ı olmayan veya süresi dolmuş bir istek parola değiştirmeye çalışır
- **THEN** istek kimlik doğrulama hatasıyla reddedilir

#### Scenario: Hız sınırı
- **WHEN** aynı hesap için kısa sürede çok sayıda parola değiştirme isteği gelir
- **THEN** sınırı aşan istekler, mevcut parola doğru olsa bile, hız sınırı hatasıyla reddedilir ve parola değişmez

#### Scenario: Tarayıcıdaki oturum da sona erer
- **WHEN** kullanıcı Hesabım'daki parola değiştirme formunu başarıyla gönderir
- **THEN** tarayıcıdaki oturum çerezleri silinir ve kullanıcı, parolasının değiştiğini ve oturumlarının kapandığını söyleyen giriş ekranına yönlendirilir

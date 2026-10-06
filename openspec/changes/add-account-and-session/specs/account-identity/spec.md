# account-identity Specification (delta)

> Bu bir **delta spec**'tir: yalnızca bu change'in getirdiği davranışı tanımlar. Onaylanıp uygulandıktan sonra `openspec/specs/account-identity/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3).

## ADDED Requirements

### Requirement: E-posta ve parola ile hesap açma

Sistem, e-posta ve parola ile hesap açmayı SHALL sağlar. Hesap doğrulanmamış durumda açılır ve kullanıcıya `ROLE_CUSTOMER` rolü verilir. Kayıt yanıtı, girilen e-postanın sistemde kayıtlı olup olmadığını **açığa çıkarmaz**: her durumda aynı durum kodu ve aynı gövde döner.

#### Scenario: Yeni e-posta ile kayıt
- **WHEN** kullanıcı sistemde bulunmayan bir e-posta ve geçerli bir parola ile kayıt olur
- **THEN** doğrulanmamış bir hesap açılır, e-posta adresine tek kullanımlık ve süreli bir doğrulama bağlantısı gönderilir ve genel bir "bağlantı gönderildi" yanıtı döner

#### Scenario: Doğrulanmamış bir hesabın e-postasıyla tekrar kayıt
- **WHEN** kullanıcı daha önce kaydolmuş ama henüz doğrulanmamış bir e-posta ile yeniden kayıt olur
- **THEN** ikinci bir hesap açılmaz, doğrulama bağlantısı yeniden gönderilir ve ilk kayıtla **aynı** yanıt döner

#### Scenario: Doğrulanmış bir hesabın e-postasıyla kayıt
- **WHEN** kullanıcı doğrulanmış bir hesaba ait e-posta ile kayıt olur
- **THEN** yeni hesap açılmaz, mevcut hesabın sahibine durumu bildiren bir e-posta gönderilir ve diğer durumlarla **aynı** yanıt döner

#### Scenario: Geçersiz kayıt bilgisi
- **WHEN** kullanıcı geçersiz biçimde bir e-posta veya kuralları karşılamayan bir parola gönderir
- **THEN** istek doğrulama hatasıyla reddedilir ve ortak hata gövdesinde hatalı alanlar bildirilir

#### Scenario: Parola asla geri okunamaz
- **WHEN** bir hesap açılır
- **THEN** parola yalnızca hash'lenmiş olarak saklanır; hiçbir uç, log kaydı veya hata mesajı parolayı düz metin olarak içermez

### Requirement: Zorunlu e-posta doğrulaması

Hesap, e-posta adresi doğrulanmadan kullanılamaz. Doğrulama token'ı tek kullanımlıktır, süre sınırlıdır ve veritabanında yalnızca hash'i saklanır.

#### Scenario: Geçerli token ile doğrulama
- **WHEN** kullanıcı süresi geçmemiş ve daha önce kullanılmamış bir doğrulama token'ı gönderir
- **THEN** hesap etkin duruma geçer, token tüketilir ve kullanıcı giriş yapabilir

#### Scenario: Token iki kez kullanılamaz
- **WHEN** kullanıcı daha önce kullanılmış bir doğrulama token'ını yeniden gönderir
- **THEN** istek reddedilir ve hesabın durumu değişmez

#### Scenario: Süresi dolmuş token
- **WHEN** kullanıcı süresi dolmuş bir doğrulama token'ı gönderir
- **THEN** istek reddedilir ve kullanıcıya yeni bir bağlantı talep etmesi gerektiği bildirilir

#### Scenario: Doğrulanmamış hesapla giriş
- **WHEN** kullanıcı doğru parolayla ama henüz doğrulanmamış bir hesapla giriş yapmaya çalışır
- **THEN** oturum açılmaz ve kullanıcıya e-postasını doğrulaması gerektiği bildirilir

### Requirement: Oturum açma

Sistem, doğrulanmış bir hesap için e-posta ve parola ile oturum açmayı SHALL sağlar. Oturum, kısa ömürlü bir erişim token'ı ve iptal edilebilir bir yenileme token'ı ile temsil edilir. Kimlik bilgileri hatalı olduğunda hangi bilginin hatalı olduğunu açığa çıkarmayan tek ve genel bir hata döner.

#### Scenario: Geçerli kimlik bilgileri
- **WHEN** kullanıcı doğrulanmış bir hesabın doğru e-posta ve parolasını gönderir
- **THEN** kısa ömürlü bir erişim token'ı ve bir yenileme token'ı döner

#### Scenario: Hatalı parola ile hatalı e-posta aynı yanıtı döner
- **WHEN** kullanıcı var olmayan bir e-posta ile ya da var olan bir hesabın hatalı parolasıyla giriş yapmaya çalışır
- **THEN** iki durumda da **aynı** hata kodu ve aynı mesaj döner; hesabın var olup olmadığı anlaşılmaz

#### Scenario: Hız sınırı
- **WHEN** aynı kaynaktan kısa sürede izin verilenden fazla giriş denemesi yapılır
- **THEN** sonraki istekler hız sınırı hatasıyla reddedilir ve pencere dolduğunda yeniden kabul edilir

### Requirement: Oturum yenileme ve sonlandırma

Yenileme token'ı tek kullanımlıktır ve her kullanımda yenisiyle değiştirilir. Daha önce kullanılmış bir yenileme token'ının sunulması sızıntı işareti sayılır ve o oturum zincirinin tamamı geçersiz kılınır. Kullanıcı oturumunu sonlandırdığında yenileme token'ı geçersiz olur.

#### Scenario: Yenileme ile yeni erişim token'ı
- **WHEN** kullanıcı geçerli bir yenileme token'ı sunar
- **THEN** yeni bir erişim token'ı ve yeni bir yenileme token'ı döner; sunulan token bir daha kullanılamaz

#### Scenario: Kullanılmış yenileme token'ının tekrar sunulması
- **WHEN** daha önce kullanılmış bir yenileme token'ı yeniden sunulur
- **THEN** istek reddedilir ve aynı oturum zincirine ait tüm yenileme token'ları geçersiz kılınır; kullanıcı yeniden giriş yapmak zorundadır

#### Scenario: Oturumu sonlandırma
- **WHEN** kullanıcı oturumunu sonlandırır
- **THEN** yenileme token'ı geçersiz olur ve sonraki yenileme denemeleri reddedilir

#### Scenario: Süresi dolmuş yenileme token'ı
- **WHEN** kullanıcı süresi dolmuş bir yenileme token'ı sunar
- **THEN** istek reddedilir ve kullanıcı giriş ekranına yönlendirilir

### Requirement: Oturum sahibinin kimliği

Sistem, oturum açmış kullanıcının kimliğini ve rollerini bildiren bir uç SHALL sunar. Bu uç kimlik doğrulaması gerektirir ve bilgiyi güncel kaynaktan okur.

#### Scenario: Oturumlu erişim
- **WHEN** geçerli bir erişim token'ı ile kimlik bilgisi talep edilir
- **THEN** hesabın e-postası ve rolleri döner

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

#### Scenario: Siteler arası istek
- **WHEN** oturum durumunu değiştiren bir arayüz isteği başka bir siteden tetiklenir
- **THEN** istek reddedilir

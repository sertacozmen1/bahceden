# account-identity Specification (delta)

> Bu bir **delta spec**'tir: bu change'in getirdiği davranışı tanımlar. Uygulanıp onaylandıktan sonra `openspec/specs/account-identity/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar: `design.md` D1–D2.

## MODIFIED Requirements

### Requirement: E-posta ile ikinci faktör, hesap bazlı anahtarla

Hesap üzerinde ikinci faktör anahtarı SHALL bulunur. Anahtar kapalıyken giriş tek adımlıdır. Anahtar açıkken giriş iki adımlıdır: birinci adım token döndürmez, e-posta ile tek kullanımlık bir kod gönderir; token'lar ancak bu kod doğrulandığında verilir. Anahtarın varsayılan değeri kapalıdır.

Oturum açmış kullanıcı anahtarı mevcut parolasını girerek açabilir veya kapatabilir. Açmak doğrulanmış bir e-posta adresi gerektirir. Değişiklik hesabın dilinde e-postayla bildirilir. Anahtarı kapatmak hesabın açık giriş kodlarını geçersiz kılar. Değişiklik açık oturumları kapatmaz. Değişiklik istekleri hesap başına hız sınırlıdır.

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

#### Scenario: Kullanıcı ikinci faktörü açar
- **WHEN** e-postası doğrulanmış, oturum açmış kullanıcı doğru mevcut parolasıyla ikinci faktörü açar
- **THEN** anahtar açılır, hesabın dilinde bildirim e-postası gider, mevcut oturum sürer ve sonraki giriş iki adımlı olur

#### Scenario: Kullanıcı ikinci faktörü kapatır
- **WHEN** ikinci faktörü açık, oturum açmış kullanıcı doğru mevcut parolasıyla ikinci faktörü kapatır
- **THEN** anahtar kapanır, bildirim e-postası gider, süresi geçmemiş giriş kodları artık kabul edilmez ve sonraki giriş tek adımlı olur

#### Scenario: Yanlış mevcut parola
- **WHEN** oturum açmış kullanıcı ikinci faktörü yanlış bir mevcut parolayla açmak veya kapatmak ister
- **THEN** istek mevcut parola alanına bağlı bir doğrulama hatasıyla reddedilir ve anahtar değişmez

#### Scenario: Doğrulanmamış e-postayla açma
- **WHEN** e-postası doğrulanmamış kullanıcı ikinci faktörü açmak ister
- **THEN** istek iş kuralı hatasıyla reddedilir ve anahtar kapalı kalır

#### Scenario: Doğrulanmamış e-postayla kapatma
- **WHEN** e-postası doğrulanmamış, ikinci faktörü açık kullanıcı doğru mevcut parolasıyla ikinci faktörü kapatır
- **THEN** anahtar kapanır; e-posta doğrulaması yalnızca açmak için gerekir

#### Scenario: Aynı değer
- **WHEN** kullanıcı anahtarı zaten bulunduğu duruma getirmek ister
- **THEN** anahtar değişmez ve bildirim e-postası gitmez

#### Scenario: Oturumsuz değişiklik isteği
- **WHEN** erişim token'ı olmayan bir istek ikinci faktörü değiştirmeye çalışır
- **THEN** istek kimlik doğrulama hatasıyla reddedilir

#### Scenario: Değişiklik hız sınırı
- **WHEN** aynı hesap için kısa sürede çok sayıda ikinci faktör değişikliği isteği gelir
- **THEN** sınırı aşan istekler, mevcut parola doğru olsa bile, hız sınırı hatasıyla reddedilir ve anahtar değişmez

#### Scenario: Doğrulanmamış e-postada ekran
- **WHEN** e-postası doğrulanmamış kullanıcı Hesabım'dan ikinci faktör sayfasını açar
- **THEN** form yerine e-postayı doğrulaması gerektiği söylenir ve yeni doğrulama bağlantısı isteyebileceği bir düğme gösterilir

### Requirement: Oturum sahibinin kimliği

Sistem, oturum açmış kullanıcının kimliğini, rollerini, e-posta doğrulama durumunu ve ikinci faktörün açık olup olmadığını bildiren bir uç SHALL sunar. Bu uç kimlik doğrulaması gerektirir ve bilgiyi güncel kaynaktan okur.

#### Scenario: Oturumlu erişim
- **WHEN** geçerli bir erişim token'ı ile kimlik bilgisi talep edilir
- **THEN** hesabın e-postası, rolleri, e-posta doğrulama durumu ve ikinci faktörün durumu döner

#### Scenario: Kimliksiz erişim
- **WHEN** erişim token'ı olmadan ya da geçersiz bir token ile kimlik bilgisi talep edilir
- **THEN** istek kimlik doğrulama hatasıyla reddedilir

#### Scenario: Rol değişikliği güncel yansır
- **WHEN** hesabın rolleri değiştikten sonra kimlik bilgisi talep edilir
- **THEN** dönen roller güncel kaynaktaki hâlini yansıtır

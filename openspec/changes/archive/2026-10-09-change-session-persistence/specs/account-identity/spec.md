# Delta: account-identity

## MODIFIED Requirements

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

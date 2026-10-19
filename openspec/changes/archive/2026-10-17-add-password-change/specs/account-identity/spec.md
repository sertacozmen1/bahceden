# account-identity Specification (delta)

> Bu bir **delta spec**'tir: bu change'in getirdiği davranışı tanımlar. Uygulanıp onaylandıktan sonra `openspec/specs/account-identity/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar: `design.md` D1–D4.

## ADDED Requirements

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

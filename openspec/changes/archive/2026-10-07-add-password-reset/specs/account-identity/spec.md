# account-identity Specification (delta)

> Bu bir **delta spec**'tir: bu change'in getirdiği davranışı tanımlar. Uygulanıp onaylandıktan sonra `openspec/specs/account-identity/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar: `design.md` D1–D4.

## ADDED Requirements

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

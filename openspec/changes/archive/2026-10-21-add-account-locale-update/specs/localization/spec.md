# localization Specification (delta)

> Bu bir **delta spec**'tir: bu change'in getirdiği davranışı tanımlar. Uygulanıp onaylandıktan sonra `openspec/specs/localization/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar: `design.md` D1–D2.

## MODIFIED Requirements

### Requirement: Hesabın dili ve e-postalar

Sistem, her hesabın dilini SHALL saklar. Hesabın dili üç anda yazılır:

- kayıtta isteğin dili;
- her başarılı girişte isteğin dili;
- oturum açık kullanıcı dil menüsünden bir dil seçtiğinde seçilen dil.

Hesaba gönderilen e-postalar hesabın dilinde gönderilir (doğrulama bağlantısı, giriş kodu, "hesabınız zaten var" bildirimi ve hesaba giden diğer e-postalar). Hesabın dili yalnızca oturum sahibi tarafından değiştirilebilir. Dil eşitlemesi başarısız olsa da ekrandaki dil değişir; hesabın dili bir sonraki girişte güncellenir.

#### Scenario: İngilizce kayıt
- **WHEN** kullanıcı İngilizce arayüzle kayıt olur
- **THEN** hesabın dili İngilizce saklanır ve doğrulama e-postası İngilizce gönderilir

#### Scenario: Giriş dili günceller
- **WHEN** Türkçe açılmış bir hesabın sahibi İngilizce arayüzle giriş yapar ve ikinci faktör açıktır
- **THEN** hesabın dili İngilizce olur ve giriş kodu e-postası İngilizce gönderilir

#### Scenario: Oturum açıkken dil değişir
- **WHEN** dili Türkçe olan hesabın sahibi, oturumu açıkken dil menüsünden "English" seçer ve ardından yeni bir doğrulama bağlantısı ister
- **THEN** sayfa İngilizce gösterilir, hesabın dili İngilizce olur ve doğrulama e-postası İngilizce gönderilir

#### Scenario: Oturum yokken dil değişir
- **WHEN** oturumu olmayan bir ziyaretçi dil menüsünden "English" seçer
- **THEN** sayfa İngilizce gösterilir ve hiçbir hesabın dili değişmez

#### Scenario: Oturumun erişim süresi dolmuşken dil değişir
- **WHEN** erişim token'ının süresi geçmiş bir kullanıcı dil menüsünden "English" seçer
- **THEN** sayfa yine İngilizce gösterilir, kullanıcıya hata gösterilmez ve hesabın dili bir sonraki girişte İngilizce olur

#### Scenario: Başkasının dili değiştirilemez
- **WHEN** bir kullanıcı hesabının dilini değiştirir
- **THEN** yalnızca kendi hesabının dili değişir; başka bir hesabı adresleyen bir yol yoktur

#### Scenario: Var olan hesaba kayıt denemesi
- **WHEN** İngilizce arayüzle, dili Türkçe olan mevcut bir hesabın e-postasıyla kayıt denenir
- **THEN** yanıt İngilizcedir, "hesabınız zaten var" bildirimi ise hesabın dilinde, Türkçe gönderilir

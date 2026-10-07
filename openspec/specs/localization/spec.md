# localization Specification

## Purpose
Kullanıcı arayüzünün, sunucunun kullanıcıya döndürdüğü metinlerin ve hesaba gönderilen e-postaların dilini tanımlar: desteklenen diller (Türkçe varsayılan, İngilizce), dilin seçilme sırası, sunucu mesajlarının dili ve hesabın saklanan dili. Ürün ve satıcı adları gibi kullanıcı verisi çevrilmez. URL'de dil öneki, üçüncü dil ve sağdan sola yazım bu capability'nin bugünkü kapsamı dışındadır.

## Requirements

### Requirement: Desteklenen diller ve varsayılan dil

Sistem, kullanıcı arayüzünü ve kullanıcıya dönen metinleri Türkçe ve İngilizce olarak SHALL sunar. Varsayılan ve kaynak dil Türkçedir. Ürün ve satıcı adları gibi kullanıcı verisi çevrilmez.

#### Scenario: Dil bilgisi olmayan ziyaretçi
- **WHEN** ziyaretçinin dil çerezi yoktur ve tarayıcısı desteklenen bir dil bildirmez
- **THEN** sayfa Türkçe gösterilir ve `<html lang="tr">` olur

#### Scenario: Desteklenmeyen dil
- **WHEN** tarayıcı yalnızca desteklenmeyen bir dil bildirir (örn. `de`)
- **THEN** sayfa Türkçe gösterilir

### Requirement: Dil seçimi

Sistem, dili sırasıyla kullanıcının kaydedilmiş seçiminden, ardından tarayıcının bildirdiği dil tercihinden SHALL belirler. Dil adreste görünmez; aynı adres her dilde aynı sayfayı gösterir.

#### Scenario: Tarayıcı İngilizce bildirir
- **WHEN** dil çerezi olmayan ziyaretçinin tarayıcısı İngilizceyi Türkçeden yüksek öncelikle bildirir (örn. `en-GB,en;q=0.9,tr;q=0.5`)
- **THEN** sayfa İngilizce gösterilir

#### Scenario: Kullanıcı dili değiştirir
- **WHEN** kullanıcı başlıktaki dil menüsünden "English" seçer
- **THEN** sayfa adres değişmeden İngilizce yeniden gösterilir ve seçim sonraki ziyaretlerde de geçerli olur

#### Scenario: Kaydedilmiş seçim tarayıcı dilini ezer
- **WHEN** kullanıcı Türkçeyi seçmiştir ve tarayıcısı İngilizce bildirir
- **THEN** sayfa Türkçe gösterilir

### Requirement: Sunucu mesajlarının dili

`api`, kullanıcıya gösterilebilir her metni (doğrulama, hata ve bilgi mesajları) isteğin bildirdiği dilde SHALL döner; desteklenmeyen veya bildirilmeyen dil için Türkçe döner. Hata kodları dilden bağımsızdır.

#### Scenario: İngilizce doğrulama hatası
- **WHEN** İngilizce bildiren bir istek kurallara uymayan bir parola ile kayıt olmayı dener
- **THEN** yanıt `VALIDATION_FAILED` koduyla döner ve hem genel mesaj hem alan mesajı İngilizcedir

#### Scenario: Dil bildirilmeyen hata
- **WHEN** dil bildirmeyen bir istek hatalı kimlik bilgisiyle giriş yapmayı dener
- **THEN** yanıt `AUTH_INVALID_CREDENTIALS` koduyla ve Türkçe mesajla döner

#### Scenario: Ekran ile sunucu mesajı aynı dildedir
- **WHEN** kullanıcı arayüzü İngilizce kullanırken bir form hatası alır
- **THEN** sunucudan gelen hata mesajı da İngilizce gösterilir

### Requirement: Hesabın dili ve e-postalar

Sistem, her hesabın dilini SHALL saklar: kayıtta isteğin dili yazılır, her başarılı girişte isteğin diliyle güncellenir. Hesaba gönderilen e-postalar (doğrulama bağlantısı, giriş kodu, "hesabınız zaten var" bildirimi) hesabın dilinde gönderilir.

#### Scenario: İngilizce kayıt
- **WHEN** kullanıcı İngilizce arayüzle kayıt olur
- **THEN** hesabın dili İngilizce saklanır ve doğrulama e-postası İngilizce gönderilir

#### Scenario: Giriş dili günceller
- **WHEN** Türkçe açılmış bir hesabın sahibi İngilizce arayüzle giriş yapar ve ikinci faktör açıktır
- **THEN** hesabın dili İngilizce olur ve giriş kodu e-postası İngilizce gönderilir

#### Scenario: Var olan hesaba kayıt denemesi
- **WHEN** İngilizce arayüzle, dili Türkçe olan mevcut bir hesabın e-postasıyla kayıt denenir
- **THEN** yanıt İngilizcedir, "hesabınız zaten var" bildirimi ise hesabın dilinde, Türkçe gönderilir

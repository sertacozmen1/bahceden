# seller-onboarding Specification

## Purpose
Üreticinin mevcut hesabından belgesiyle satıcı olarak başvurmasını, admin'in başvuruyu onaylamasını, gerekçeyle reddetmesini, askıya almasını ve askıdan çıkarmasını, kararların hesabın dilinde bildirilmesini ve her durum geçişinin silinmeyen bir geçmişte tutulmasını tanımlar. Kararlar: ADR-037, ADR-040…046. Ürün ilanı ekleme bu capability'nin bugünkü kapsamı dışındadır.

## Requirements

### Requirement: Satıcı başvurusu

Sistem, oturum açmış ve e-postası doğrulanmış bir hesabın üretici olarak başvurmasını SHALL sağlar. Başvuruda vitrinde görünecek üretici adı, şehir, iletişim telefonu, belge türü (`CKS_CERTIFICATE` veya `TAX_CERTIFICATE`) ve belge dosyası bulunur. Başvuru `PENDING` durumunda açılır. Bir hesabın en fazla bir satıcı kaydı olur.

#### Scenario: Geçerli başvuru
- **WHEN** e-postası doğrulanmış bir hesap tüm alanları ve kabul edilen türde bir belgeyi gönderir
- **THEN** satıcı kaydı `PENDING` durumunda oluşur, yanıt kaydın durumunu döner ve belge saklanır

#### Scenario: Oturum olmadan başvuru
- **WHEN** kimlik bilgisi olmadan başvuru gönderilir
- **THEN** istek 401 ile reddedilir ve kayıt oluşmaz

#### Scenario: E-postası doğrulanmamış hesap
- **WHEN** e-postası doğrulanmamış bir hesap başvuru gönderir
- **THEN** istek iş kuralı hatasıyla reddedilir, kayıt oluşmaz ve hata kullanıcıyı e-postasını doğrulamaya yönlendirir

#### Scenario: İkinci başvuru
- **WHEN** satıcı kaydı zaten olan bir hesap yeni başvuru gönderir
- **THEN** istek çakışma hatasıyla reddedilir ve mevcut kayıt değişmez

#### Scenario: Eksik veya kurala uymayan alan
- **WHEN** üretici adı, şehir veya telefon eksiktir ya da kurala uymaz, veya belge türü tanınmaz
- **THEN** istek alan bazlı doğrulama hatasıyla reddedilir ve hiçbir dosya saklanmaz

#### Scenario: Hız sınırı
- **WHEN** aynı hesaptan kısa sürede çok sayıda başvuru veya yeniden gönderim gelir
- **THEN** sınırı aşan istekler hız sınırı hatasıyla reddedilir

### Requirement: Belge dosyası doğrulanır ve yalnızca yetkili kişilere sunulur

Sistem belgenin türünü istemcinin bildirdiği türe ve dosya adına bakarak değil, içeriğindeki imzaya bakarak SHALL belirler. Kabul edilen türler PDF, JPEG ve PNG'dir. Boyut üst sınırı yapılandırmadadır. Dosya sunucunun ürettiği bir adla saklanır. Belgeyi yalnızca sahibi olan hesap ve `ROLE_ADMIN` indirebilir. Belgenin herkese açık bir adresi yoktur.

#### Scenario: Kabul edilmeyen tür
- **WHEN** içeriği PDF, JPEG veya PNG olmayan bir dosya, uzantısı `.pdf` olsa bile, gönderilir
- **THEN** istek doğrulama hatasıyla reddedilir ve dosya saklanmaz

#### Scenario: Boyut sınırını aşan dosya
- **WHEN** yapılandırılmış sınırdan büyük bir dosya gönderilir
- **THEN** istek doğrulama hatasıyla reddedilir ve dosya saklanmaz

#### Scenario: Sahibi belgesini indirir
- **WHEN** hesap kendi satıcı kaydının belgesini ister
- **THEN** belge, tespit edilen türüyle ve tarayıcının içeriği yorumlamasını engelleyen başlıklarla döner

#### Scenario: Başkasının belgesi
- **WHEN** admin olmayan bir hesap başka bir satıcının belgesini ister
- **THEN** istek 404 ile reddedilir ve belgenin varlığı açığa çıkmaz

### Requirement: Satıcı kendi kaydını görür ve reddedilen başvuruyu yeniden gönderir

Sistem, hesabın kendi satıcı kaydını (durum, beyan alanları, belge türü, varsa ret veya askı gerekçesi) görmesini SHALL sağlar. `REJECTED` durumundaki kayıt düzeltilmiş alanlar ve yeni bir belgeyle yeniden gönderilebilir; kayıt tekrar `PENDING` olur.

#### Scenario: Kaydı olmayan hesap
- **WHEN** satıcı kaydı olmayan bir hesap kendi kaydını ister
- **THEN** 404 döner; arayüz bunu başvuruya davet olarak gösterir

#### Scenario: Reddedilen başvurunun yeniden gönderilmesi
- **WHEN** `REJECTED` durumundaki kaydın sahibi düzeltilmiş alanlar ve yeni bir belgeyle yeniden gönderir
- **THEN** kayıt `PENDING` olur, yeni belge saklanır ve önceki belge denetim izi için korunur

#### Scenario: Reddedilmemiş kaydı yeniden gönderme
- **WHEN** `PENDING`, `APPROVED` veya `SUSPENDED` durumundaki kaydın sahibi yeniden gönderir
- **THEN** istek çakışma hatasıyla reddedilir ve kayıt değişmez

### Requirement: Admin başvuruları karara bağlar

Sistem, `ROLE_ADMIN` rolündeki hesabın satıcıları duruma göre süzerek listelemesini, detayını (hesap e-postası, telefon, belge, durum geçmişi dahil) görmesini ve şu geçişleri yapmasını SHALL sağlar:

| Eylem | Geçiş | Gerekçe |
|---|---|---|
| Onay | `PENDING` → `APPROVED` | yok |
| Ret | `PENDING` → `REJECTED` | zorunlu |
| Askıya alma | `APPROVED` → `SUSPENDED` | zorunlu |
| Askıdan çıkarma | `SUSPENDED` → `APPROVED` | yok |

Tablodaki geçişler dışındaki her eylem reddedilir.

#### Scenario: Onay rol verir
- **WHEN** admin `PENDING` bir kaydı onaylar
- **THEN** kayıt `APPROVED` olur ve sahibi olan hesaba `ROLE_SELLER` eklenir

#### Scenario: Gerekçeli ret
- **WHEN** admin `PENDING` bir kaydı gerekçe yazarak reddeder
- **THEN** kayıt `REJECTED` olur, gerekçe kayda yazılır ve hesabın rolleri değişmez

#### Scenario: Gerekçesiz ret veya askı
- **WHEN** admin ret veya askı eylemini gerekçe olmadan gönderir
- **THEN** istek doğrulama hatasıyla reddedilir ve kayıt değişmez

#### Scenario: Askıya alma rolü kaldırır
- **WHEN** admin `APPROVED` bir satıcıyı gerekçe yazarak askıya alır
- **THEN** kayıt `SUSPENDED` olur ve hesaptan `ROLE_SELLER` kaldırılır

#### Scenario: Askıdan çıkarma rolü geri verir
- **WHEN** admin `SUSPENDED` bir satıcıyı askıdan çıkarır
- **THEN** kayıt `APPROVED` olur ve hesaba `ROLE_SELLER` yeniden eklenir

#### Scenario: Geçersiz geçiş
- **WHEN** admin tabloda olmayan bir geçiş dener (örneğin `REJECTED` bir kaydı onaylar)
- **THEN** istek çakışma hatasıyla reddedilir ve kayıt değişmez

#### Scenario: Admin olmayan hesap
- **WHEN** `ROLE_ADMIN` rolü olmayan bir hesap admin uçlarından birini çağırır
- **THEN** istek 403 ile reddedilir

#### Scenario: Durum süzgeci ve sayfalama
- **WHEN** admin listeyi bir durumla süzer
- **THEN** yalnızca o durumdaki kayıtlar, ortak liste şeklinde ve sunucuda sınırlanmış sayfa boyutuyla döner

### Requirement: Kararlar bildirilir ve denetim izi saklanır

Sistem her durum geçişinde kimin yaptığını, ne zaman yaptığını, önceki ve yeni durumu ve varsa gerekçeyi silinmeyen bir kayıt olarak SHALL saklar. Bu kayıt başvuru ve yeniden gönderimi de kapsar. Admin kararları, hesabın dilinde e-postayla hesap sahibine bildirilir. E-posta gönderilemezse karar geçerli kalır.

#### Scenario: Geçmiş tutulur
- **WHEN** bir kayıt başvurulur, reddedilir, yeniden gönderilir ve onaylanır
- **THEN** admin detayında bu dört geçiş, yapanı ve zamanıyla birlikte sırayla görünür

#### Scenario: Karar e-postası hesabın dilinde
- **WHEN** admin İngilizce dilli bir hesabın başvurusunu reddeder
- **THEN** hesap sahibine İngilizce bir e-posta gider ve e-postada ret gerekçesi yer alır

#### Scenario: E-posta gönderilemez
- **WHEN** karar e-postası gönderilemez
- **THEN** karar yine kaydedilir ve admin başarılı yanıt alır

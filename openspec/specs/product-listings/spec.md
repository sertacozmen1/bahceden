# product-listings Specification

## Purpose
Onaylı satıcının ürün ilanı oluşturmasını, düzenlemesini, 1–5 fotoğraf yükleyip sıralamasını ve ilanı yayına almasını, yayından çekmesini, arşivlemesini; admin'in uygunsuz ilanı gerekçeyle kaldırmasını ve yeniden açmasını, her geçişin silinmeyen bir geçmişte tutulmasını tanımlar. Kararlar: ADR-038, ADR-039, ADR-047…053. Sepet, sipariş, stok düşme, kategori ve arama bu capability'nin bugünkü kapsamı dışındadır.

## Requirements

### Requirement: Onaylı satıcı ilan oluşturur ve düzenler

Sistem, satıcı kaydı `APPROVED` olan bir hesabın ürün ilanı oluşturmasını ve kendi ilanlarını listeleyip düzenlemesini SHALL sağlar. İlanda ad, isteğe bağlı açıklama, satış birimi (`KILOGRAM`, `PIECE`, `BUNCH`), kuruş cinsinden birim fiyat ve stok miktarı bulunur. Yeni ilan `DRAFT` durumunda açılır ve vitrinde görünmez. Her işlem önce onaylı satıcı kapısından, sonra sorgu seviyesindeki sahiplik denetiminden geçer.

#### Scenario: Taslak ilan oluşturma
- **WHEN** onaylı satıcı kurallara uyan bir ilan gönderir
- **THEN** ilan `DRAFT` durumunda oluşur, satıcının ilan listesinde görünür ve vitrinde görünmez

#### Scenario: Onaylı olmayan satıcı
- **WHEN** satıcı kaydı olmayan, bekleyen, reddedilen veya askıdaki bir hesap ilan uçlarından birini çağırır
- **THEN** istek 403 ile reddedilir ve hiçbir ilan oluşmaz veya değişmez

#### Scenario: Başkasının ilanı
- **WHEN** onaylı satıcı başka bir satıcının ilanını görmeye, düzenlemeye veya ona fotoğraf yüklemeye çalışır
- **THEN** istek 404 ile reddedilir ve ilanın varlığı açığa çıkmaz

#### Scenario: Kurala uymayan alanlar
- **WHEN** ad eksik veya çok uzundur, fiyat sıfır ya da negatiftir, stok negatiftir veya birim tanınmaz
- **THEN** istek alan bazlı doğrulama hatasıyla reddedilir ve ilan değişmez

#### Scenario: Yayındaki ilanı düzenleme
- **WHEN** satıcı yayındaki ilanının fiyatını veya stokunu değiştirir
- **THEN** değişiklik kaydedilir ve vitrinde hemen görünür; ilan yayında kalır

### Requirement: İlan fotoğrafları

Bir ilanın en fazla 5 fotoğrafı SHALL olur; sıradaki ilk fotoğraf kapaktır. Satıcı fotoğrafları tek tek yükler, siler ve sıralar. Fotoğrafın türü içeriğinden tespit edilir; kabul edilen türler JPEG ve PNG'dir. Fotoğraf sunucuda okunur, EXIF yön etiketine göre döndürülür ve iki boyutta JPEG olarak yeniden kodlanır. Yeniden kodlanan dosyada orijinal dosyanın meta verisi bulunmaz. Fotoğraflar sunucunun ürettiği tahmin edilemez anahtarlarla, uzun süreli önbellek başlığıyla sunulur.

#### Scenario: Fotoğraf yükleme
- **WHEN** satıcı kendi ilanına geçerli bir JPEG veya PNG yükler
- **THEN** fotoğraf ilanın sonuna eklenir ve iki boyutu da anahtarıyla sunulabilir olur

#### Scenario: Altıncı fotoğraf
- **WHEN** 5 fotoğrafı olan bir ilana yeni fotoğraf yüklenir
- **THEN** istek reddedilir ve fotoğraf eklenmez

#### Scenario: Kabul edilmeyen dosya
- **WHEN** içeriği JPEG veya PNG olmayan, okunamayan, boyut sınırını aşan ya da piksel sınırını aşan bir dosya yüklenir
- **THEN** istek alan bazlı doğrulama hatasıyla reddedilir ve hiçbir dosya saklanmaz

#### Scenario: Meta veri atılır ve yön uygulanır
- **WHEN** EXIF yön etiketi "90 derece döndür" ve konum bilgisi taşıyan bir JPEG yüklenir
- **THEN** sunulan fotoğraf döndürülmüş olarak, EXIF ve konum bilgisi olmadan döner

#### Scenario: Sıralama ve kapak
- **WHEN** satıcı fotoğrafların sırasını değiştirir
- **THEN** yeni sıradaki ilk fotoğraf kapak olur ve vitrin kartında o görünür

#### Scenario: Geçersiz sıralama
- **WHEN** gönderilen sıralama ilanın fotoğraflarının tamamını tam olarak bir kez içermez
- **THEN** istek reddedilir ve sıra değişmez

#### Scenario: Fotoğraf silme
- **WHEN** satıcı bir fotoğrafı siler
- **THEN** fotoğraf ilandan ve depolamadan kaldırılır; yayındaki bir ilanın son fotoğrafı silinemez

### Requirement: İlan yayın durumları

İlan durumları ve izinli geçişler şunlardır; tablodaki geçişler dışındaki her eylem SHALL reddedilir:

| Eylem | Kim | Geçiş | Koşul |
|---|---|---|---|
| Yayına al | Satıcı | `DRAFT` → `PUBLISHED` | En az bir fotoğraf |
| Yayından çek | Satıcı | `PUBLISHED` → `DRAFT` | - |
| Arşivle | Satıcı | `DRAFT` veya `PUBLISHED` → `ARCHIVED` | - |
| Arşivden geri al | Satıcı | `ARCHIVED` → `DRAFT` | - |
| Kaldır | Admin | `DRAFT` veya `PUBLISHED` → `REMOVED` | Gerekçe zorunlu |
| Yeniden aç | Admin | `REMOVED` → `PUBLISHED` | En az bir fotoğraf |

#### Scenario: Fotoğrafsız ilanı yayına alma
- **WHEN** satıcı fotoğrafı olmayan bir taslağı yayına almaya çalışır
- **THEN** istek iş kuralı hatasıyla reddedilir ve ilan taslak kalır

#### Scenario: Yayına alma
- **WHEN** satıcı fotoğrafı olan bir taslağı yayına alır
- **THEN** ilan `PUBLISHED` olur ve satıcısı onaylı olduğu sürece vitrinde görünür

#### Scenario: Geçersiz geçiş
- **WHEN** satıcı tabloda olmayan bir geçiş dener (örneğin arşivdeki ilanı doğrudan yayına almak)
- **THEN** istek çakışma hatasıyla reddedilir ve ilan değişmez

### Requirement: Admin ilanı kaldırır ve yeniden açar

Sistem, `ROLE_ADMIN` rolündeki hesabın ilanları durumuna göre süzerek listelemesini, bir ilanı gerekçe yazarak kaldırmasını ve kaldırılmış ilanı yeniden açmasını SHALL sağlar. Kaldırılan ilan vitrinde görünmez. Satıcı gerekçeyi görür ve ilanı düzenleyebilir, ama kendisi yayına alamaz. Kaldırma ve yeniden açma satıcıya, hesabının dilinde e-postayla bildirilir.

#### Scenario: Gerekçeli kaldırma
- **WHEN** admin yayındaki bir ilanı gerekçe yazarak kaldırır
- **THEN** ilan `REMOVED` olur, vitrinden ve ürün detayından düşer, gerekçe satıcıya gösterilir ve satıcıya e-posta gider

#### Scenario: Satıcı kaldırılan ilanı yayına alamaz
- **WHEN** satıcı kaldırılmış ilanını yayına almaya çalışır
- **THEN** istek çakışma hatasıyla reddedilir

#### Scenario: Satıcı kaldırılan ilanı düzeltir
- **WHEN** satıcı kaldırılmış ilanının bilgilerini veya fotoğraflarını değiştirir
- **THEN** değişiklik kaydedilir ve ilan `REMOVED` kalır

#### Scenario: Yeniden açma
- **WHEN** admin kaldırılmış bir ilanı yeniden açar
- **THEN** ilan `PUBLISHED` olur, gerekçe temizlenir ve satıcıya e-posta gider

#### Scenario: Admin olmayan hesap
- **WHEN** admin rolü olmayan bir hesap admin ilan uçlarından birini çağırır
- **THEN** istek 403 ile reddedilir

### Requirement: İlan geçmişi saklanır

Sistem ilanın her durum geçişinde önceki ve yeni durumu, gerekçeyi, geçişi yapan hesabı ve zamanı silinmeyen bir kayıt olarak SHALL saklar.

#### Scenario: Geçmiş tutulur
- **WHEN** bir ilan yayına alınır, admin tarafından kaldırılır ve yeniden açılır
- **THEN** üç geçiş, yapanı ve gerekçesiyle birlikte sırayla kayıtlıdır

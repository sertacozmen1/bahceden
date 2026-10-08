# seller-payout-details Specification (delta)

> Bu bir **delta spec**'tir ve yeni bir capability tanımlar. Uygulanıp onaylandıktan sonra `openspec/specs/seller-payout-details/spec.md` olarak ana spec'lere sync edilir (`AGENTS.md` §5.3). Çerçeve karar: [ADR-054](../../../../../../docs/adr/054-split-payment-via-provider-port-provider-pending.md).

## ADDED Requirements

### Requirement: Onaylı satıcı ödeme bilgilerini girer ve günceller

Sistem, satıcı kaydı `APPROVED` olan bir hesabın ödeme bilgilerini girmesini ve güncellemesini SHALL sağlar. Ödeme bilgileri şunlardır:

- satıcı türü: `INDIVIDUAL`, `SOLE_PROPRIETOR` veya `COMPANY`;
- yasal ad;
- kimlik numarası: `INDIVIDUAL` ve `SOLE_PROPRIETOR` için TCKN, `COMPANY` için VKN;
- vergi dairesi: `SOLE_PROPRIETOR` ve `COMPANY` için zorunlu;
- IBAN.

Kayıt veya güncelleme, aynı istekte ödeme sağlayıcısında alt üye işyeri kaydını açar veya günceller. Sağlayıcı kaydı kabul ederse ödeme hesabı `ACTIVE` olur.

#### Scenario: Geçerli ödeme bilgileri
- **WHEN** onaylı satıcı kurallara uyan ödeme bilgilerini gönderir
- **THEN** ödeme hesabı `ACTIVE` olur ve satıcının satın alınabilirlik koşullarını sağlayan ürünleri satın alınabilir hale gelir

#### Scenario: Onaylı olmayan satıcı
- **WHEN** satıcı kaydı olmayan, bekleyen, reddedilen veya askıdaki bir hesap ödeme bilgisi gönderir
- **THEN** istek 403 ile reddedilir ve kayıt oluşmaz

#### Scenario: Geçersiz kimlik numarası veya IBAN
- **WHEN** TCKN'nin sağlama haneleri tutmaz, VKN 10 hane değildir veya IBAN'ın mod-97 sağlaması tutmaz
- **THEN** istek ilgili alana bağlı doğrulama hatasıyla reddedilir ve kayıt değişmez

#### Scenario: Türle uyuşmayan kimlik numarası
- **WHEN** `COMPANY` türünde 11 haneli bir numara veya `INDIVIDUAL` türünde 10 haneli bir numara gönderilir
- **THEN** istek kimlik numarası alanına bağlı doğrulama hatasıyla reddedilir

#### Scenario: Sağlayıcı kaydı reddeder
- **WHEN** ödeme sağlayıcısı alt üye kaydını reddeder
- **THEN** ödeme hesabı `PROVIDER_REJECTED` olur, istek iş kuralı hatası döner ve satıcının ürünleri satın alınamaz kalır

### Requirement: Ödeme bilgileri maskelenir ve loglanmaz

Sistem IBAN'ı ve kimlik numarasını hiçbir yanıtta tam olarak SHALL döndürmez; yalnızca son dört hane görünür. Bu değerler uygulama log'una SHALL yazılmaz. Satıcı bir değeri değiştirmek için onu yeniden tam olarak girer.

#### Scenario: Kendi ödeme bilgisini okuma
- **WHEN** satıcı kendi ödeme hesabını ister
- **THEN** yanıt satıcı türünü, yasal adı, vergi dairesini, durumu, kimlik numarasının ve IBAN'ın yalnızca son dört hanesini içerir

#### Scenario: Ödeme hesabı olmayan satıcı
- **WHEN** ödeme hesabı olmayan onaylı satıcı kendi ödeme hesabını ister
- **THEN** 404 döner; arayüz bunu ödeme bilgilerini tamamlamaya davet olarak gösterir

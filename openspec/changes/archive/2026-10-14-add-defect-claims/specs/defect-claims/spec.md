## ADDED Requirements

### Requirement: Alıcı teslimden sonra 48 saat içinde ayıplı ürün talebi açar

Sistem, alıcının `DELIVERED` durumundaki kendi satıcı siparişi için, gönderinin teslim anından itibaren 48 saat içinde bir ayıplı ürün talebi açmasını SHALL sağlar.

Talepte şunlar bulunur:
- bir veya birden çok satır; her satır için 1 ile satın alınan adet arasında bir adet;
- 10–1000 karakter açıklama;
- 1–3 fotoğraf.

Fotoğraflar yeniden boyutlandırılır ve konum bilgisi atılır. Satıcı siparişi başına tek talep açılabilir. Satıcıya 48 saatlik yanıt süresi başlar ve satıcıya e-posta gider.

#### Scenario: Talep açılır
- **WHEN** alıcı teslimden 10 saat sonra iki satırlık bir satıcı siparişinin bir satırı için 2 adet, açıklama ve bir fotoğrafla talep açar
- **THEN** talep `OPEN` olur, satıcının son tarihi 48 saat sonrasıdır ve satıcıya hesap dilinde e-posta gider

#### Scenario: Pencere kapandı
- **WHEN** alıcı teslimden 49 saat sonra talep açmak ister
- **THEN** istek iş kuralı hatasıyla reddedilir ve talep oluşmaz

#### Scenario: Teslim edilmemiş sipariş
- **WHEN** alıcı `SHIPPED` durumundaki satıcı siparişi için talep açmak ister
- **THEN** istek çakışma hatasıyla reddedilir

#### Scenario: İkinci talep
- **WHEN** talebi olan satıcı siparişi için yeniden talep açılmak istenir
- **THEN** istek çakışma hatasıyla reddedilir

#### Scenario: Fotoğrafsız veya fazla fotoğraflı talep
- **WHEN** talep fotoğrafsız ya da dört fotoğrafla gönderilir
- **THEN** istek fotoğraf alanına bağlı doğrulama hatasıyla reddedilir

#### Scenario: Geçersiz satır
- **WHEN** talep satın alınandan fazla adet ya da başka bir satıcı siparişinin satırını içerir
- **THEN** istek ilgili alana bağlı doğrulama hatasıyla reddedilir

#### Scenario: Başkasının siparişi
- **WHEN** alıcı başka bir hesabın siparişi için talep açmak ister
- **THEN** istek 404 ile reddedilir

### Requirement: Satıcı talebi kabul eder, kısmi iade önerir veya gerekçeyle reddeder

Sistem, onaylı satıcının `OPEN` durumundaki kendi talebine yanıt vermesini SHALL sağlar. Yanıt üç türden biridir:

- **kabul:** istenen adetlerin tamamı iade edilir, talep `REFUNDED` olur;
- **kısmi teklif:** her satırda istenen adeti aşmayan ve toplamda en az bir adet içeren daha az adet önerilir; talep `OFFERED` olur;
- **ret:** gerekçe 10–500 karakterdir; talep `REJECTED` olur.

Teklif ve ret, alıcıya 48 saatlik yanıt süresi başlatır. Satıcı bir yanıtın para etkisini, yan etkisiz bir önizleme ucuyla önceden görebilir.

#### Scenario: Satıcı kabul eder
- **WHEN** satıcı `OPEN` talebi kabul eder
- **THEN** istenen adetlerin tutarı iade edilir, talep `REFUNDED` olur ve iki tarafa sonuç e-postası gider

#### Scenario: Satıcı kısmi teklif verir
- **WHEN** satıcı 2 adet istenen satır için 1 adet önerir
- **THEN** talep `OFFERED` olur, alıcının son tarihi 48 saat sonrasıdır ve alıcıya teklif e-postası gider

#### Scenario: Teklif istenenden fazla
- **WHEN** satıcı bir satırda alıcının istediğinden fazla adet önerir
- **THEN** istek doğrulama hatasıyla reddedilir

#### Scenario: Gerekçesiz ret
- **WHEN** satıcı gerekçe yazmadan reddeder
- **THEN** istek doğrulama hatasıyla reddedilir ve talep değişmez

#### Scenario: Başka satıcının talebi
- **WHEN** satıcı başka bir satıcının satıcı siparişindeki talebe yanıt verir
- **THEN** istek 404 ile reddedilir

#### Scenario: Yanıtlanmış talep
- **WHEN** satıcı `OFFERED` durumundaki talebe yeniden yanıt verir
- **THEN** istek çakışma hatasıyla reddedilir

### Requirement: Alıcı teklifi veya reddi kabul eder ya da yönetime taşır

Sistem, alıcının `OFFERED` veya `REJECTED` durumundaki kendi talebi için 48 saat içinde iki yanıttan birini vermesini SHALL sağlar:

- **kabul:** teklif kabul edilirse teklif edilen adetler iade edilir ve talep `REFUNDED` olur; ret kabul edilirse talep `CLOSED` olur;
- **yönetime taşıma:** talep `ESCALATED` olur ve iki tarafa e-posta gider.

#### Scenario: Teklif kabul edilir
- **WHEN** alıcı `OFFERED` talebi kabul eder
- **THEN** teklif edilen adetlerin tutarı iade edilir ve talep `REFUNDED` olur

#### Scenario: Ret yönetime taşınır
- **WHEN** alıcı `REJECTED` talebi yönetime taşır
- **THEN** talep `ESCALATED` olur ve alıcıya ve satıcıya e-posta gider

#### Scenario: Açık talepte alıcı eylemi
- **WHEN** alıcı `OPEN` durumundaki talebi kabul etmek ister
- **THEN** istek çakışma hatasıyla reddedilir

### Requirement: Süresi dolan talepler kendiliğinden ilerler

Sistem, süresi dolan talepleri zamanlanmış bir işle SHALL ilerletir:

| Durum | Hangi süre dolduğunda | Sonuç |
|---|---|---|
| `OPEN` | satıcının süresi | `ESCALATED` |
| `OFFERED` | alıcının süresi | teklif edilen adetlerle iade, `REFUNDED` |
| `REJECTED` | alıcının süresi | `CLOSED` |

Bu geçişlerde eylemci yoktur. İade başarısız olursa talep değişmez ve sonraki çalışmada yeniden denenir. Aynı talep aynı anda bir kullanıcı eylemi ve süre işi tarafından işlenirse yalnızca biri uygulanır; iade iki kez yapılmaz.

#### Scenario: Satıcı yanıt vermedi
- **WHEN** `OPEN` talebin satıcı süresi dolar ve süre işi çalışır
- **THEN** talep `ESCALATED` olur

#### Scenario: Alıcı teklife yanıt vermedi
- **WHEN** `OFFERED` talebin alıcı süresi dolar ve süre işi çalışır
- **THEN** teklif edilen adetler iade edilir ve talep `REFUNDED` olur

#### Scenario: İade başarısız
- **WHEN** süre işi teklifi uygularken ödeme sağlayıcısı iadeyi reddeder
- **THEN** talep `OFFERED` kalır ve iade kaydı oluşmaz

#### Scenario: Çift işleme yok
- **WHEN** alıcı teklifi kabul ederken süre işi aynı talebi işler
- **THEN** yalnızca bir iade yapılır

### Requirement: Admin yönetime taşınan talebi karara bağlar

Sistem, admin'in talepleri durumla süzerek listelemesini, talebin detayını fotoğraflarıyla görmesini ve `ESCALATED` talebi karara bağlamasını SHALL sağlar. Karar iki türlüdür:

- **iade:** her satırda satın alınan adete kadar, toplamda en az bir adet; talep `REFUNDED` olur;
- **ret:** gerekçe 10–500 karakter; talep `CLOSED` olur.

Karar ve gerekçe admin hesabıyla geçmişe yazılır; iki tarafa e-posta gider.

#### Scenario: Admin kısmi iade kararı verir
- **WHEN** admin `ESCALATED` talepte bir satırın 1 adetini iade eder
- **THEN** o tutar iade edilir, talep `REFUNDED` olur ve iki tarafa e-posta gider

#### Scenario: Admin reddeder
- **WHEN** admin gerekçeyle reddeder
- **THEN** talep `CLOSED` olur ve para hareketi olmaz

#### Scenario: Karar bekleyen olmayan talep
- **WHEN** admin `OPEN` talebe karar vermek ister
- **THEN** istek çakışma hatasıyla reddedilir

#### Scenario: Admin olmayan
- **WHEN** admin rolü olmayan bir hesap talep listesini ister
- **THEN** istek 403 ile reddedilir

### Requirement: İade tutarı ve yeni hakediş tek kuralla hesaplanır

Sistem, iade tutarını ve yeni hakedişi satıcı siparişine sipariş anında yazılmış komisyon oranı ve sabit ücretle SHALL hesaplar.

**İade tutarı:** her satırda birim fiyat × iade adedi toplamıdır.

**Tam iade** (her satırın satın alınan bütün adetleri):
- kargo ücreti de iade edilir;
- komisyon ve hakediş sıfır olur.

**Kısmi iade:**
- kargo ücreti iade edilmez;
- komisyon = kalan ürün tutarının yüzde kısmı (`HALF_UP`) + sabit ücret; kalan tutarı aşamaz;
- hakediş = kalan ürün tutarı − komisyon.

Stok geri eklenmez. İade, satıcının alt üye payından `DEFECT` nedeniyle yapılır.

#### Scenario: Kısmi iade
- **WHEN** ürün tutarı 20000 kuruş, oran %5, sabit 300 kuruş olan satıcı siparişinde 5000 kuruşluk adet iade edilir
- **THEN** alıcıya 5000 kuruş iade edilir, yeni komisyon 1050 kuruş, yeni hakediş 13950 kuruş olur

#### Scenario: Tam iade
- **WHEN** satıcı siparişinin her satırının her adeti iade edilir
- **THEN** ürün tutarı ile kargo ücreti iade edilir, komisyon ve hakediş sıfır olur

#### Scenario: Stok değişmez
- **WHEN** bir talep iadeyle sonuçlanır
- **THEN** ürünlerin stoğu değişmez

### Requirement: Talep fotoğrafları yalnızca taraflara korumalı sunulur

Sistem, talep fotoğraflarını yalnızca o siparişin alıcısına, satıcısına ve admin'e SHALL sunar. Sunum içerik türü korumalı ve önbelleğe alınmaz biçimdedir. Açıklama, gerekçe ve fotoğraf anahtarı uygulama log'una yazılmaz.

#### Scenario: Başkası fotoğrafı ister
- **WHEN** talebin tarafı olmayan bir hesap fotoğrafı ister
- **THEN** istek 404 ile reddedilir

#### Scenario: Oturumsuz istek
- **WHEN** fotoğraf oturum olmadan istenir
- **THEN** istek 401 ile reddedilir

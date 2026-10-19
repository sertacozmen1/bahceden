# shipments Specification

## Purpose
Satıcının kargo gönderisi açmasını, etiketin korumalı sunumunu, gönderi durumunun entegratörden izlenmesini ve teslim edilemeyen siparişte admin kararını tanımlar. Kararlar: ADR-057, ADR-071…ADR-078. Gerçek entegratör seçimi (KALANLAR A2) bu capability'nin bugünkü kapsamı dışındadır. Teslim zamanı ayıplı ürün talebinin penceresini başlatır; talebin kendisi `defect-claims` capability'sindedir (ADR-081).

## Requirements

### Requirement: Satıcı kargoya hazır siparişi için gönderi açar

Sistem, onaylı satıcının `NEW` durumundaki kendi satıcı siparişi için koli boyutunu (`SMALL`, `MEDIUM`, `LARGE`) seçerek kargo entegratöründe gönderi açmasını SHALL sağlar. Gönderi açılırken entegratöre şunlar iletilir:

- gönderici: satıcının görünen adı, ili ve telefonu (paket şubeye teslim edilir);
- alıcı: siparişe kopyalanmış teslimat adresindeki ad, telefon, il, ilçe, açık adres ve posta kodu;
- koli boyutu ve satıcı siparişinin referansı.

Entegratörün döndürdüğü kargo firması, takip numarası ve etiket kaydedilir. Bir satıcı siparişinin aynı anda tek bir etkin gönderisi olur; gönderi açılmışken tekrarlanan istek mevcut gönderiyi döndürür. Entegratör hata verirse hiçbir şey kaydedilmez. Her işlem önce onaylı satıcı kapısından, sonra sorgu seviyesindeki sahiplik denetiminden geçer.

#### Scenario: Gönderi açılır
- **WHEN** onaylı satıcı `NEW` durumundaki kendi satıcı siparişi için orta boy koli seçerek gönderi açar
- **THEN** gönderi `CREATED` durumunda kaydedilir, yanıtta kargo firması ve takip numarası bulunur, etiket satıcının etiket ucundan alınabilir ve satıcı siparişi `NEW` kalır

#### Scenario: Tekrarlanan istek
- **WHEN** satıcı gönderisi zaten açılmış bir satıcı siparişi için yeniden gönderi açar
- **THEN** entegratörde yeni gönderi açılmaz ve mevcut gönderi döner

#### Scenario: Uygun olmayan durum
- **WHEN** satıcı iptal edilmiş veya kargoya verilmiş bir satıcı siparişi için gönderi açmak ister
- **THEN** istek çakışma hatasıyla reddedilir

#### Scenario: Başka satıcının siparişi
- **WHEN** satıcı başka bir satıcının satıcı siparişi için gönderi açmak veya etiketini almak ister
- **THEN** istek 404 ile reddedilir

#### Scenario: Geçersiz koli boyutu
- **WHEN** satıcı koli boyutu olmadan veya tanımsız bir boyutla gönderi açmak ister
- **THEN** istek alan bazlı doğrulama hatasıyla reddedilir

#### Scenario: Entegratör hatası
- **WHEN** entegratör gönderi oluşturmayı reddeder veya ulaşılamaz
- **THEN** gönderi kaydedilmez, satıcı siparişi değişmez ve istek entegratör hatası döner

#### Scenario: Gönderici telefonu yok
- **WHEN** telefonu kayıtlı olmayan bir satıcı gönderi açmak ister
- **THEN** istek iş kuralı hatasıyla reddedilir ve entegratör çağrılmaz

### Requirement: Etiket yalnızca satıcısına korumalı olarak sunulur

Sistem gönderi etiketini yalnızca o satıcı siparişinin sahibi olan onaylı satıcıya SHALL sunar. Yanıt entegratörün bildirdiği içerik türüyle, `X-Content-Type-Options: nosniff` ve `Content-Security-Policy: sandbox` başlıklarıyla döner ve önbelleğe alınmaz. Etiket kişisel veri içerdiği için vitrinden veya herkese açık bir adresten sunulmaz.

#### Scenario: Satıcı etiketi alır
- **WHEN** satıcı kendi gönderisinin etiketini ister
- **THEN** etiket içerik türü ve koruyucu başlıklarla döner

#### Scenario: Oturum olmadan
- **WHEN** kimlik bilgisi olmadan etiket istenir
- **THEN** istek 401 ile reddedilir

### Requirement: Gönderi durumu entegratörden izlenir

Sistem, `CREATED`, `HANDED_OVER` ve `IN_TRANSIT` durumundaki gönderilerin durumunu yapılandırılmış aralıkla entegratörden SHALL sorgular. Her gönderi ayrı transaction'da güncellenir; bir gönderinin hatası diğerlerini durdurmaz. Gönderi durumu satıcı siparişine şöyle yansır:

| Gönderi | Satıcı siparişi |
|---|---|
| `HANDED_OVER` (kargoya verildi) | `SHIPPED`; kargoya verilme zamanı yazılır |
| `IN_TRANSIT` (yolda) | `SHIPPED` (değişmez) |
| `DELIVERED` (teslim edildi) | `DELIVERED`; teslim zamanı yazılır |
| `DELIVERY_FAILED` (teslim edilemedi) | `DELIVERY_FAILED` |

Entegratör ara durumları atlarsa satıcı siparişi aradaki geçişleri sırayla yapar. Her satıcı siparişi geçişi, yapan taraf sistem olarak sipariş geçmişine yazılır. Geri giden (eski bir duruma dönen) bildirim yok sayılır. Kargoya verilme ve teslim e-postaları yalnızca satıcı siparişi geçişi gerçekten uygulandığında gönderilir.

#### Scenario: Kargoya verildi
- **WHEN** entegratör gönderinin kargoya verildiğini bildirir
- **THEN** satıcı siparişi `SHIPPED` olur, kargoya verilme zamanı yazılır ve alıcıya kargo firmasını ve takip numarasını içeren e-posta gider

#### Scenario: Teslim edildi
- **WHEN** entegratör gönderinin teslim edildiğini bildirir
- **THEN** satıcı siparişi `DELIVERED` olur, teslim zamanı yazılır ve alıcıya, hesabının dilinde, siparişin teslim edildiğini, sorun bildirimi için son anı Türkiye saatiyle ve sipariş bağlantısını içeren e-posta gider

#### Scenario: Teslim e-postasındaki son an sipariş detayındakiyle aynıdır
- **WHEN** teslim e-postası gönderilir
- **THEN** e-postadaki son an, teslim zamanına talep süresi eklenerek hesaplanır ve alıcının sipariş detayında gördüğü talep son anıyla aynıdır

#### Scenario: Ara durum atlanır
- **WHEN** `CREATED` durumundaki gönderi için entegratör doğrudan teslim edildi bildirir
- **THEN** satıcı siparişi önce `SHIPPED`, sonra `DELIVERED` olur, iki geçiş de geçmişe yazılır ve alıcıya önce kargoya verildi, sonra teslim edildi e-postası gider

#### Scenario: Teslim bildirimi yinelenir
- **WHEN** teslim edilmiş bir satıcı siparişi için aynı teslim bildirimi yeniden işlenir
- **THEN** satıcı siparişi ve geçmişi değişmez ve ikinci bir teslim e-postası gitmez

#### Scenario: Teslim edilemedi
- **WHEN** entegratör gönderinin teslim edilemediğini bildirir
- **THEN** satıcı siparişi `DELIVERY_FAILED` olur ve alıcıya ve satıcıya hesap dillerinde e-posta gider

#### Scenario: Bir gönderinin sorgusu başarısız olur
- **WHEN** entegratör bir gönderinin sorgusunda hata verir
- **THEN** o gönderi değişmez, sonraki sorguda yeniden denenir ve diğer gönderiler güncellenir

### Requirement: Teslim edilemeyen siparişte admin karar verir

Sistem, admin'in `DELIVERY_FAILED` durumundaki satıcı siparişlerini (ve karara bağlanmış `REFUNDED` / `CLOSED` olanları) listelemesini, detayını görmesini ve karar bekleyenler için şu kararlardan birini vermesini SHALL sağlar:

- `REFUND`: satıcı siparişinin ürün tutarı ile kargo ücretinin toplamı ödeme sağlayıcısından iade edilir; komisyon ve hakediş geçersiz olur; stok geri eklenmez; satıcı siparişi `REFUNDED` olur. İade başarısız olursa karar gerçekleşmez.
- `CLOSE`: gerekçe zorunludur; para hareketi olmaz; satıcı siparişi `CLOSED` olur.

Karar ve gerekçesi sipariş geçmişine admin tarafından yapılmış olarak yazılır; alıcıya ve satıcıya hesap dillerinde e-posta gider. Admin detayında alıcının adı ve telefonu görünür (iletişim için); açık adres gösterilmez.

#### Scenario: Admin iade eder
- **WHEN** admin teslim edilemeyen bir satıcı siparişi için `REFUND` kararı verir
- **THEN** ürün tutarı ve kargo ücreti iade edilir, satıcı siparişi `REFUNDED` olur, stok değişmez ve iki tarafa e-posta gider

#### Scenario: Admin gerekçeyle kapatır
- **WHEN** admin `CLOSE` kararını gerekçe yazarak verir
- **THEN** satıcı siparişi `CLOSED` olur, iade yapılmaz ve gerekçe geçmişe yazılır

#### Scenario: Gerekçesiz kapatma
- **WHEN** admin `CLOSE` kararını gerekçesiz verir
- **THEN** istek doğrulama hatasıyla reddedilir

#### Scenario: Uygun olmayan durum
- **WHEN** admin teslim edilmiş veya kargoda olan bir satıcı siparişi için karar vermek ister
- **THEN** istek çakışma hatasıyla reddedilir

#### Scenario: Admin olmayan
- **WHEN** admin rolü olmayan bir hesap bu uçlardan birini çağırır
- **THEN** istek 403 ile reddedilir

### Requirement: Geliştirme ve testte gönderi dışarı çıkmaz

Sistem yalnızca yapılandırmada adı verilen entegratörle SHALL çalışır; bugün geçerli tek değer `fake`'tir ve tanımsız değerle uygulama açılmaz. `fake` entegratör ağ çağrısı yapmaz, kişisel veri taşımayan ve "örnek, geçersiz" yazılı bir PDF etiket üretir ve gönderiyi açılışından itibaren yapılandırılmış sürelerle `HANDED_OVER`, `IN_TRANSIT` ve `DELIVERED` durumlarına ilerletir.

#### Scenario: Sahte gönderi ilerler
- **WHEN** `fake` entegratörde açılan bir gönderi için yapılandırılmış teslim süresi geçer ve durum sorgulanır
- **THEN** gönderi `DELIVERED` döner

#### Scenario: Tanımsız entegratör
- **WHEN** uygulama `bahceden.shipping.integrator` için `fake` dışında bir değerle açılır
- **THEN** uygulama açılışta hata vererek durur

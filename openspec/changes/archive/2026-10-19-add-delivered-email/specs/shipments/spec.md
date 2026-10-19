# shipments Specification (delta)

> Bu bir **delta spec**'tir: bu change'in getirdiği davranışı tanımlar. Uygulanıp onaylandıktan sonra `openspec/specs/shipments/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar: `design.md` D1–D2.

## MODIFIED Requirements

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

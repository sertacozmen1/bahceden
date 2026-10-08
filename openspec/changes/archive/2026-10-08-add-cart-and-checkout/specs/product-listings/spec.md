# product-listings Specification (delta)

> Bu bir **delta spec**'tir. Uygulanıp onaylandıktan sonra `openspec/specs/product-listings/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Karar: [ADR-058](../../../../../../docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md) (kargoya verme süresi ilanda beyan edilir).

## MODIFIED Requirements

### Requirement: Onaylı satıcı ilan oluşturur ve düzenler

Sistem, satıcı kaydı `APPROVED` olan bir hesabın ürün ilanı oluşturmasını ve kendi ilanlarını listeleyip düzenlemesini SHALL sağlar. İlanda şunlar bulunur:

- ad;
- isteğe bağlı açıklama;
- satış birimi (`KILOGRAM`, `PIECE`, `BUNCH`);
- kuruş cinsinden birim fiyat;
- stok miktarı;
- **kargoya verme süresi** (1, 2 veya 3 iş günü).

Kargoya verme süresi istekte gönderilmezse yeni ilanda 2 iş günü olur; güncellemede mevcut değer korunur. Yeni ilan `DRAFT` durumunda açılır ve vitrinde görünmez. Her işlem önce onaylı satıcı kapısından, sonra sorgu seviyesindeki sahiplik denetiminden geçer.

#### Scenario: Taslak ilan oluşturma
- **WHEN** onaylı satıcı kurallara uyan bir ilan gönderir
- **THEN** ilan `DRAFT` durumunda oluşur, satıcının ilan listesinde görünür ve vitrinde görünmez

#### Scenario: Kargoya verme süresi verilmeden ilan
- **WHEN** onaylı satıcı kargoya verme süresi belirtmeden yeni bir ilan oluşturur
- **THEN** ilanın kargoya verme süresi 2 iş günü olur

#### Scenario: Onaylı olmayan satıcı
- **WHEN** satıcı kaydı olmayan, bekleyen, reddedilen veya askıdaki bir hesap ilan uçlarından birini çağırır
- **THEN** istek 403 ile reddedilir ve hiçbir ilan oluşmaz veya değişmez

#### Scenario: Başkasının ilanı
- **WHEN** onaylı satıcı başka bir satıcının ilanını görmeye, düzenlemeye veya ona fotoğraf yüklemeye çalışır
- **THEN** istek 404 ile reddedilir ve ilanın varlığı açığa çıkmaz

#### Scenario: Kurala uymayan alanlar
- **WHEN** ad eksik veya çok uzundur, fiyat sıfır ya da negatiftir, stok negatiftir, birim tanınmaz veya kargoya verme süresi 1–3 dışındadır
- **THEN** istek alan bazlı doğrulama hatasıyla reddedilir ve ilan değişmez

#### Scenario: Yayındaki ilanı düzenleme
- **WHEN** satıcı yayındaki ilanının fiyatını veya stokunu değiştirir
- **THEN** değişiklik kaydedilir ve vitrinde hemen görünür; ilan yayında kalır

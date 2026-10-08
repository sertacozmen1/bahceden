# storefront-catalog Specification (delta)

> Bu bir **delta spec**'tir. Uygulanıp onaylandıktan sonra `openspec/specs/storefront-catalog/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Karar: [ADR-038](../../../../../../docs/adr/038-listings-publish-immediately-with-admin-takedown.md) §4, `design.md` D2.

## MODIFIED Requirements

### Requirement: Vitrinde yayınlanmış ürünlerin listelenmesi

Sistem, yayın durumu `PUBLISHED` olan **ve satıcısı `APPROVED` durumunda olan** ürünleri kimlik doğrulaması gerektirmeden listelemeyi SHALL sağlar. Liste her ürün için adını, birimini, birim fiyatını, stok durumunu ve ürünün üreticisini (ad ve şehir) içerir. `DRAFT` ve `ARCHIVED` durumundaki ürünler listede SHALL yer almaz. Satıcısı `PENDING`, `REJECTED` veya `SUSPENDED` durumunda olan ürünler de, kendi yayın durumları ne olursa olsun, listede SHALL yer almaz.

#### Scenario: Yayınlanmış ürünler listelenir
- **WHEN** istemci ürün listesini kimlik bilgisi olmadan talep eder
- **THEN** yalnızca satıcısı `APPROVED` olan `PUBLISHED` ürünler, her biri üretici adı ve şehriyle birlikte döner

#### Scenario: Yayınlanmamış ürün gizlenir
- **WHEN** bir ürün `DRAFT` veya `ARCHIVED` durumundadır
- **THEN** o ürün vitrin listesinde görünmez

#### Scenario: Onaylı olmayan satıcının ürünü gizlenir
- **WHEN** `PUBLISHED` bir ürünün satıcısı askıya alınır
- **THEN** ürün vitrin listesinden ve toplam sayıdan düşer; satıcı askıdan çıkarıldığında ürün yeniden görünür

#### Scenario: Hiç yayınlanmış ürün yok
- **WHEN** sistemde yayınlanmış ürün bulunmaz
- **THEN** boş bir liste ve toplam sayı sıfır döner; hata üretilmez ve ekranda kullanıcıyı yönlendiren bir boş durum gösterilir

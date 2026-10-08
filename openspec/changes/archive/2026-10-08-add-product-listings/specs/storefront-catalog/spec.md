# storefront-catalog Specification (delta)

> Bu bir **delta spec**'tir. Uygulanıp onaylandıktan sonra `openspec/specs/storefront-catalog/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar: `design.md` D5, D6.

## MODIFIED Requirements

### Requirement: Vitrinde yayınlanmış ürünlerin listelenmesi

Sistem, yayın durumu `PUBLISHED` olan **ve satıcısı `APPROVED` durumunda olan** ürünleri kimlik doğrulaması gerektirmeden listelemeyi SHALL sağlar. Liste her ürün için adını, birimini, birim fiyatını, stok durumunu, ürünün üreticisini (ad ve şehir) ve varsa kapak fotoğrafını içerir. `DRAFT`, `ARCHIVED` ve `REMOVED` durumundaki ürünler listede SHALL yer almaz. Satıcısı `PENDING`, `REJECTED` veya `SUSPENDED` durumunda olan ürünler de, kendi yayın durumları ne olursa olsun, listede SHALL yer almaz.

#### Scenario: Yayınlanmış ürünler listelenir
- **WHEN** istemci ürün listesini kimlik bilgisi olmadan talep eder
- **THEN** yalnızca satıcısı `APPROVED` olan `PUBLISHED` ürünler, her biri üretici adı ve şehriyle birlikte döner

#### Scenario: Yayınlanmamış ürün gizlenir
- **WHEN** bir ürün `DRAFT`, `ARCHIVED` veya `REMOVED` durumundadır
- **THEN** o ürün vitrin listesinde görünmez

#### Scenario: Onaylı olmayan satıcının ürünü gizlenir
- **WHEN** `PUBLISHED` bir ürünün satıcısı askıya alınır
- **THEN** ürün vitrin listesinden ve toplam sayıdan düşer; satıcı askıdan çıkarıldığında ürün yeniden görünür

#### Scenario: Kapak fotoğrafı
- **WHEN** listedeki bir ürünün fotoğrafları vardır
- **THEN** ürün sıradaki ilk fotoğrafı kapak olarak taşır; fotoğrafı olmayan ürünün kapak alanı boştur ve liste hata üretmez

#### Scenario: Hiç yayınlanmış ürün yok
- **WHEN** sistemde yayınlanmış ürün bulunmaz
- **THEN** boş bir liste ve toplam sayı sıfır döner; hata üretilmez ve ekranda kullanıcıyı yönlendiren bir boş durum gösterilir

## ADDED Requirements

### Requirement: Ürün detayı

Sistem, vitrinde görünen bir ürünün detayını kalıcı adresiyle (`slug`), kimlik doğrulaması gerektirmeden SHALL sunar. Detay; adı, açıklamayı, birimi, birim fiyatı, stok durumunu, üreticiyi (ad ve şehir) ve fotoğrafları sıralı olarak içerir. Vitrin listesinde görünmeyecek bir ürünün detayı istendiğinde, ürün yokmuş gibi yanıt verilir.

#### Scenario: Yayındaki ürünün detayı
- **WHEN** istemci yayında olan ve satıcısı onaylı bir ürünün adresini ister
- **THEN** ürün bütün fotoğraflarıyla, sıralı olarak döner

#### Scenario: Görünmeyen ürünün detayı
- **WHEN** istenen ürün yoktur, taslaktır, arşivdedir, kaldırılmıştır veya satıcısı onaylı değildir
- **THEN** 404 döner ve ekranda "bulunamadı" durumu gösterilir

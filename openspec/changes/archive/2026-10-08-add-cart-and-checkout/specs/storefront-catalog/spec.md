# storefront-catalog Specification (delta)

> Bu bir **delta spec**'tir. Uygulanıp onaylandıktan sonra `openspec/specs/storefront-catalog/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar:
> - [ADR-054](../../../../../../docs/adr/054-split-payment-via-provider-port-provider-pending.md): ödeme bilgisi eksik satıcının ürünü satın alınamaz;
> - [ADR-058](../../../../../../docs/adr/058-flat-shipping-fee-per-seller-and-declared-handover.md): kargoya verme süresi.

## MODIFIED Requirements

### Requirement: Ürün detayı

Sistem, vitrinde görünen bir ürünün detayını kalıcı adresiyle (`slug`), kimlik doğrulaması gerektirmeden SHALL sunar. Detay şunları içerir:

- ad ve açıklama;
- birim ve birim fiyat;
- stok durumu;
- üretici (ad ve şehir);
- sıralı fotoğraflar;
- **kargoya verme süresi**;
- **ürünün satın alınabilir olup olmadığı**.

Vitrin listesinde görünmeyecek bir ürünün detayı istendiğinde, ürün yokmuş gibi yanıt verilir.

#### Scenario: Yayındaki ürünün detayı
- **WHEN** istemci yayında olan ve satıcısı onaylı bir ürünün adresini ister
- **THEN** ürün bütün fotoğraflarıyla, sıralı olarak, kargoya verme süresi ve satın alınabilirlik bilgisiyle döner

#### Scenario: Görünmeyen ürünün detayı
- **WHEN** istenen ürün yoktur, taslaktır, arşivdedir, kaldırılmıştır veya satıcısı onaylı değildir
- **THEN** 404 döner ve ekranda "bulunamadı" durumu gösterilir

## ADDED Requirements

### Requirement: Satın alınabilirlik

Sistem, vitrin listesindeki ve ürün detayındaki her ürün için satın alınabilir olup olmadığını SHALL bildirir. Bir ürün yalnızca şu dört koşulun hepsi sağlandığında satın alınabilirdir:

1. yayındadır;
2. satıcısı onaylıdır;
3. satıcısının ödeme hesabı etkindir;
4. stoğu sıfırdan büyüktür.

Satın alınamayan ürün vitrinde görünmeye devam eder. Ekranda sepete ekleme eylemi yerine nedeni gösterilir: tükendi ya da satıcı henüz satışa hazır değil.

#### Scenario: Ödeme hesabı olmayan satıcının ürünü
- **WHEN** yayındaki bir ürünün onaylı satıcısının etkin ödeme hesabı yoktur
- **THEN** ürün vitrinde görünür, satın alınamaz olarak işaretlenir ve ekranda "satıcı henüz satışa hazır değil" bilgisi gösterilir

#### Scenario: Tükenen ürün
- **WHEN** yayındaki bir ürünün stoğu sıfırdır
- **THEN** ürün vitrinde görünür, satın alınamaz olarak işaretlenir ve ekranda "tükendi" bilgisi gösterilir

#### Scenario: Satın alınabilir ürün
- **WHEN** ürün dört koşulun hepsini sağlar
- **THEN** ürün satın alınabilir olarak işaretlenir ve detay sayfasında sepete ekleme eylemi gösterilir

# defect-claims Specification (delta)

> Bu bir **delta spec**'tir: bu change'in getirdiği davranışı tanımlar. Uygulanıp onaylandıktan sonra `openspec/specs/defect-claims/spec.md` ana spec'ine sync edilir (`AGENTS.md` §5.3). Kararlar: `design.md` D1–D2.

## MODIFIED Requirements

### Requirement: Alıcı teklifi veya reddi kabul eder ya da yönetime taşır

Sistem, alıcının `OFFERED` veya `REJECTED` durumundaki kendi talebi için 48 saat içinde iki yanıttan birini vermesini SHALL sağlar:

- **kabul:** teklif kabul edilirse teklif edilen adetler iade edilir ve talep `REFUNDED` olur; ret kabul edilirse talep `CLOSED` olur;
- **yönetime taşıma:** talep `ESCALATED` olur; iki tarafa ve her admin hesabına e-posta gider.

#### Scenario: Teklif kabul edilir
- **WHEN** alıcı `OFFERED` talebi kabul eder
- **THEN** teklif edilen adetlerin tutarı iade edilir ve talep `REFUNDED` olur

#### Scenario: Ret yönetime taşınır
- **WHEN** alıcı `REJECTED` talebi yönetime taşır
- **THEN** talep `ESCALATED` olur, alıcıya ve satıcıya e-posta gider ve her admin hesabına, kendi dilinde, alıcının talebi yönetime taşıdığını ve admin talep sayfasının bağlantısını içeren e-posta gider

#### Scenario: Açık talepte alıcı eylemi
- **WHEN** alıcı `OPEN` durumundaki talebi kabul etmek ister
- **THEN** istek çakışma hatasıyla reddedilir

### Requirement: Süresi dolan talepler kendiliğinden ilerler

Sistem, süresi dolan talepleri zamanlanmış bir işle SHALL ilerletir:

| Durum | Hangi süre dolduğunda | Sonuç |
|---|---|---|
| `OPEN` | satıcının süresi | `ESCALATED`; iki tarafa ve her admin hesabına e-posta |
| `OFFERED` | alıcının süresi | teklif edilen adetlerle iade, `REFUNDED` |
| `REJECTED` | alıcının süresi | `CLOSED` |

Bu geçişlerde eylemci yoktur. İade başarısız olursa talep değişmez ve sonraki çalışmada yeniden denenir. Başarısız bir talep, aynı çalışmada süresi dolmuş diğer taleplerin işlenmesini engellemez; bir çalışmada her talep en çok bir kez denenir. Aynı talep aynı anda bir kullanıcı eylemi ve süre işi tarafından işlenirse yalnızca biri uygulanır; iade iki kez yapılmaz.

#### Scenario: Satıcı yanıt vermedi
- **WHEN** `OPEN` talebin satıcı süresi dolar ve süre işi çalışır
- **THEN** talep `ESCALATED` olur ve her admin hesabına, kendi dilinde, üreticinin süresinde yanıt vermediğini ve admin talep sayfasının bağlantısını içeren e-posta gider

#### Scenario: Admin hesabı yok
- **WHEN** hiçbir hesapta admin rolü yokken bir talep yönetime düşer
- **THEN** talep `ESCALATED` olur ve alıcıya ve satıcıya e-posta yine gider

#### Scenario: Alıcı teklife yanıt vermedi
- **WHEN** `OFFERED` talebin alıcı süresi dolar ve süre işi çalışır
- **THEN** teklif edilen adetler iade edilir ve talep `REFUNDED` olur

#### Scenario: İade başarısız
- **WHEN** süre işi teklifi uygularken ödeme sağlayıcısı iadeyi reddeder
- **THEN** talep `OFFERED` kalır ve iade kaydı oluşmaz

#### Scenario: Başarısız talepler arkadakileri bekletmez
- **WHEN** süresi dolmuş taleplerin en eskileri, bir çalışmanın bir seferde okuduğu sayıdan fazla olacak kadar çok ve hepsinin iadesi başarısızdır
- **THEN** aynı çalışmada arkadaki süresi dolmuş talepler de işlenir, başarısız talepler değişmeden kalır ve her biri bu çalışmada yalnızca bir kez denenir

#### Scenario: Çift işleme yok
- **WHEN** alıcı teklifi kabul ederken süre işi aynı talebi işler
- **THEN** yalnızca bir iade yapılır

# [ADR-014] Geliştirme verisi, dev bağlamına bağlı tekrarlanabilir bir changeset ile gelir

**Tarih:** 2026-10-06
**Durum:** Accepted · mekanizma [ADR-016](016-liquibase-for-schema-migrations.md) ile güncellendi (2026-10-06)

> [!NOTE]
> Kararın özü değişmedi: geliştirme verisi, üretime asla bulaşmayan,
> idempotent ve tekrarlanabilir bir veritabanı betiğiyle gelir. Yalnızca
> **mekanizma** değişti ([ADR-016](016-liquibase-for-schema-migrations.md)):
> Flyway `R__` betiği + profile bağlı `locations` yerine, `contextFilter:dev`
> ile işaretli ve `runOnChange:true` taşıyan bir **Liquibase changeset'i**.
>
> Uygulamada bir tuzak çıktı ve kurala dönüştü: Liquibase çalışma anında hiç
> bağlam verilmezse bağlam etiketli changeset'leri **de** uygular. Bu yüzden
> her ortam bağlamını açıkça bildirir (taban `default`, geliştirme
> `default,dev`) ve izolasyon bir testle sabitlenmiştir
> (`DevSeedIsolationTest`).

**Kaynak:** `add-storefront-product-listing` change, `design.md` D5

---

## Context

`AGENTS.md` K6 kodda hardcode mock veriyi yasaklıyor, ama bir boşluk bırakıyor: ekranın dolu halini görmeden dört durum (yükleniyor, boş, hata, yetkisiz) doğrulanamaz ve geliştirici her açılışta elle veri girmek zorunda kalır.

İhtiyaç: geliştirmede her zaman hazır, tekrarlanabilir, üretime asla bulaşmayan örnek veri.

## Decision

Geliştirme verisi bir **tekrarlanabilir** changeset ile (`db/changelog/002-dev-seed-catalog.sql`) gelir; `contextFilter:dev` ile işaretlidir ve yalnızca aktif bağlamlar arasında `dev` varsa uygulanır. Taban yapılandırma bağlamı `default` olduğu için üretim ve test ortamlarında **uygulanmaz**.

Kurallar:

- **Idempotent**: `on conflict do nothing`; tekrar çalıştığında kopya üretmez.
- **Tamamen sahte**: gerçek üretici adı, adres, telefon veya e-posta içermez (`AGENTS.md` K10). Örnek alan adı `ornek.test`.
- **Negatif örnekler dahil**: vitrinde görünmemesi gereken `DRAFT` ve `ARCHIVED` kayıtlar bilinçli olarak seed'de durur; süzgecin ve boş durumun elle doğrulanması bunu gerektirir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Bileşen veya servis içinde sabit kodlanmış örnek dizi | K6 ihlali: mock veri üretim koduna karışır ve silinmesi unutulur. |
| Bağlamsız (her ortamda çalışan) changeset ile seed | Üretim veritabanına da uygulanır; sahte kayıt üretim verisine girer. |
| Bağlam etiketi koyup çalışma anında bağlam bildirmemek | Sessiz başarısızlık: Liquibase bağlam verilmediğinde etiketli changeset'i de uygular. Bu hata bir kez yapıldı ve testle kapatıldı. |
| Uygulama açılışında Java tabanlı seed (`ApplicationRunner`) | Veri kurulumu uygulama koduna taşınır ve koşul kontrolü (`@Profile`) unutulduğunda üretime sızar; SQL betiği hem daha okunabilir hem profil ayrımı yapılandırmada. |
| Testlerin seed verisine dayanması | Testler kendi verisini kurar; paylaşılan seed'e dayanan test, seed değişince kırılır ve neden kırıldığı anlaşılmaz. |

## Consequences

### Olumlu
- Geliştirici `docker compose up` sonrası dolu bir vitrin görür; dört durumun hepsi gerçek veriyle denenebilir.
- Üretim verisine sahte kayıt girme yolu yapılandırma seviyesinde kapalı.
- Seed, süzgeç davranışının canlı belgesi: yayınlanmamış kayıtlar bilinçli olarak orada.

### Olumsuz
- Seed ile şema birlikte değişmek zorunda: tabloya zorunlu bir kolon eklendiğinde changeset de güncellenmeli, yoksa dev ortamı kırılır.
- `runOnChange:true` taşıyan changeset, dosya her değiştiğinde yeniden koşar; ağır bir seed ileride açılışı yavaşlatabilir.

### Nötr / Trade-off
- Testler seed'den bağımsız olduğu için seed yalnızca insan doğrulaması içindir; otomatik testlerde hiçbir güvence sağlamaz.

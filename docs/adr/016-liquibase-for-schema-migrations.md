# [ADR-016] Şema değişiklikleri Liquibase ile yönetilir

**Tarih:** 2026-10-06
**Durum:** Accepted
**Supersedes:** [ADR-004](004-postgresql-with-flyway.md) (yalnızca migration aracı kısmı; PostgreSQL kararı aynen geçerli)

---

## Context

[ADR-004](004-postgresql-with-flyway.md) iki şey karara bağlamıştı: veritabanı **PostgreSQL** olacak ve şema **yalnızca Flyway** ile değişecek. Liquibase o kararda "Flyway ile aynı işi yapıyor; düz SQL ekibin okuduğu dil olduğu için Flyway tercih edildi" gerekçesiyle reddedilmişti.

Proje sahibi bu tercihi tersine çevirdi: veritabanı takibi **Liquibase** ile yapılacak, PostgreSQL aynı kalacak. Karar, aracın yetenek farkından çok ekibin aşinalığı ve tercihidir; bu, mimari kararı değiştirmek için yeterli bir gerekçedir ve ADR-004'ün reddettiği alternatifin hiçbir teknik engeli yoktu.

Değişiklik ilk dilim henüz archive edilmeden ve üretim veritabanı hiç oluşmadan geldi: dönüşüm maliyeti bu yüzden en düşük olacağı anda ödeniyor.

## Decision

Şema **yalnızca Liquibase** ile değişir. ADR-004'ün diğer tüm kuralları aynen korunur:

- Veritabanı **PostgreSQL 17**; geliştirme, test ve üretim aynı motor.
- `spring.jpa.hibernate.ddl-auto` her ortamda **`validate`**; `update` ve `create-drop` yasak.
- **Uygulanmış bir changeset düzenlenmez ve yeniden adlandırılmaz**; düzeltme yeni bir changeset'tir. (Liquibase checksum doğrular: değiştirilen bir changeset uygulamayı açılışta düşürür — Flyway ile aynı koruma.)
- Veri kaybı riski taşıyan değişiklik kullanıcı onayı gerektirir (`AGENTS.md` §9).
- Para, stok ve sipariş durumu değiştiren akışlar tek transaction ve yarış koşuluna karşı korumalı; `critical` testle sabitlenir.

**Liquibase'e özgü kurallar:**

| Konu | Karar |
|---|---|
| Changelog biçimi | **Formatted SQL** (`--liquibase formatted sql`). Düz SQL, ekibin okuduğu dil: ADR-004'ün Flyway için verdiği gerekçe burada da geçerli, XML/YAML değişiklik seti soyutlamasına ihtiyaç yok |
| Master changelog | `db/changelog/db.changelog-master.yaml`; yalnızca `include` listesi tutar, SQL içermez |
| Dosya adı | `NNN-kebab-case.sql`; numara sıralıdır ve atlanmaz |
| Changeset kimliği | `--changeset bahceden:NNN-kebab-case`; dosya adıyla aynı |
| Geri alma | Her changeset `--rollback` satırları taşır. Flyway'de olmayan bu yetenek bilinçli olarak kullanılır: geri alma planı artefakt haline gelir |
| Geliştirme verisi | Changeset **`contextFilter:dev`** ile işaretlenir ([ADR-014](014-dev-seed-via-profiled-repeatable-migration.md) bu mekanizmayla güncellendi) |
| Tekrar çalışma | Seed changeset'i `runOnChange:true` taşır (Flyway `R__` davranışının karşılığı) |
| **Bağlam bildirimi** | Her ortam `spring.liquibase.contexts` değerini **açıkça** bildirir: taban `default`, geliştirme `default,dev`. Liquibase, bağlam verilmezse bağlam etiketli changeset'leri **de** uygular; yani etiket tek başına koruma sağlamaz |
| Yorum yazımı | Changelog yorumlarında `changeset` kelimesinin ardından `ad:değer` biçimi kullanılmaz: ayrıştırıcı bunu gerçek bir bildirim sanıp fantom changeset üretir |

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Flyway'de kalmak (ADR-004) | Proje sahibinin açık tercihi Liquibase; aracın teknik bir üstünlüğü bu kararı bozmuyor ve dönüşüm maliyeti şu an neredeyse sıfır (üretim veritabanı yok). |
| Liquibase'in XML/YAML changeset biçimi | Veritabanı bağımsızlığı sunar ama bu projede tek motor var (PostgreSQL). SQL'i bir soyutlamanın arkasına koymak okunabilirliği düşürür ve kısmi indeks gibi PostgreSQL'e özgü ifadeleri zorlaştırır. |
| İki aracı birlikte kullanmak (geçiş dönemi) | İki migration geçmişi, iki kilit tablosu ve iki doğruluk kaynağı demek. `AGENTS.md` §15 iki alternatifi birlikte uygulamayı yasaklıyor. |
| Değişikliği ertelemek (dilim archive edildikten sonra) | Dönüşüm maliyeti zamanla artar: her yeni migration dosyası iki kez yazılmak zorunda kalır. |

## Consequences

### Olumlu
- Geri alma (`rollback`) artık changeset'in parçası: geri dönüş planı dokümanda değil, çalıştırılabilir halde.
- Changeset bağlamları (`context`) geliştirme verisini profil yapılandırmasından çıkarıp changelog'un kendisine taşıyor: seed'in neden ve nerede çalıştığı tek yerde okunuyor.
- Checksum doğrulaması Flyway ile aynı korumayı sağlıyor: uygulanmış changeset'i değiştirmek uygulamayı düşürür.

### Olumsuz
- **İki sessiz tuzak uygulama sırasında ortaya çıktı ve ikisi de gerçek hataya dönüştü:** (1) açıklama yorumunda geçen `changeset ... context:dev` ifadesi fantom bir changeset üretti; (2) `contextFilter:dev` etiketi, çalışma anında bağlam bildirilmediği için filtrelemedi ve dev seed'i test ortamında çalıştı. İkisi de düzeltildi, ikincisi `DevSeedIsolationTest` ile sabitlendi. Liquibase'in bu davranışları belgelenmiş ama sezgisel değil.
- **Dönüşüm maliyeti ödendi:** mevcut `V1__create_catalog.sql` ve `R__dev_seed_catalog.sql` dosyaları changelog biçimine taşındı, bağımlılık ve yapılandırma değişti, ADR-004 ve ADR-014 güncellendi. Bu, kararın gecikmesinin bedelidir ve tekrarlanmaması gerekir.
- Liquibase iki yardımcı tablo oluşturur (`DATABASECHANGELOG`, `DATABASECHANGELOGLOCK`); şema Flyway'in tek tablosuna göre biraz daha kalabalık.
- Changeset kimliği + yazar + dosya adı üçlüsü elle tutarlı tutulmak zorunda; Flyway'de bu bilgi yalnızca dosya adındaydı.
- `./mvnw flyway:validate` benzeri bir doğrulama komutu yok (Liquibase Maven eklentisi bütçeye eklenmedi); doğrulama uygulama açılışında yapılır.

### Nötr / Trade-off
- Liquibase'in veritabanı bağımsızlığı bu projede kullanılmıyor: tek motor PostgreSQL ve changeset'ler düz SQL. Araç bu yüzden yeteneklerinin bir kısmıyla kullanılıyor; ihtiyaç doğarsa kalanı hazır.
- [ADR-002](002-dependency-budget.md) bütçesinde `flyway-core` + `flyway-database-postgresql` yerini `liquibase-core` aldı: kalem sayısı azaldı.

# [ADR-004] PostgreSQL ve şema değişikliği yalnızca Flyway ile

**Tarih:** 2026-10-06
**Durum:** **Superseded by [ADR-016](016-liquibase-for-schema-migrations.md)** (migration aracı kısmı; 2026-10-06)

> [!WARNING]
> Bu ADR'nin **PostgreSQL** kararı aynen yürürlüktedir. **Flyway** kararı
> [ADR-016](016-liquibase-for-schema-migrations.md) ile değiştirilmiştir: şema
> artık Liquibase ile yönetilir. Aşağıdaki Flyway'e özgü kurallar (dosya adı,
> `R__` betikleri) geçerli değildir; ADR-016'daki karşılıkları geçerlidir.

---

## Context

Pazaryeri verisi ilişkisel ve işlemsel: sipariş bir satıcıya, kaleme ve gönderime bağlı; stok ve para aynı transaction içinde değişiyor. Veri kaybı veya tutarsızlık doğrudan ticari zarar demek.

Şema zamanla değişecek ve değişimin izi gerekiyor: hangi kolon ne zaman eklendi, üretimde hangi sürüm var, yeni geliştirici sıfırdan aynı şemayı nasıl kuruyor.

JPA'nın `ddl-auto: update` kolaylığı bu noktada tuzak: şemayı sessizce değiştirir, iki ortamı birbirinden ayırır ve geri alma imkânı bırakmaz.

## Decision

- Veritabanı **PostgreSQL 17**. Hem geliştirme hem test hem üretim aynı motor.
- Şema **yalnızca Flyway** ile değişir: `api/src/main/resources/db/migration/V<n>__<kebab_case>.sql`.
- `spring.jpa.hibernate.ddl-auto` her ortamda **`validate`**; `update` ve `create-drop` yasak. Entity ile şema uyuşmazsa uygulama ayağa kalkmaz.
- Uygulanmış bir migration **düzenlenmez ve yeniden adlandırılmaz**; düzeltme yeni bir migration'dır.
- Veri migration'ları idempotent yazılır ve geri alma planı dokümanlanır.
- Veri kaybı riski taşıyan migration (kolon/tablo silme, tip daraltma, `NOT NULL` ekleme) kullanıcı onayı gerektirir (AGENTS.md §9).
- Para, stok ve sipariş durumu değiştiren akışlar tek transaction içinde ve yarış koşuluna karşı korumalıdır (`@Version` iyimser kilit veya satır kilidi); bu davranış `critical` testle sabitlenir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `ddl-auto: update` (migration aracı yok) | Şemayı sessizce değiştiriyor, geri alınamıyor, ortamlar ayrışıyor ve değişimin izi kalmıyor. Üretimde kabul edilemez. |
| Liquibase | Flyway ile aynı işi yapıyor; düz SQL dosyaları ekibin okuduğu dil olduğu için Flyway tercih edildi (XML/YAML değişiklik seti soyutlamasına ihtiyaç yok). |
| MySQL / MariaDB | PostgreSQL'in kısmi indeks, `jsonb`, dışlama kısıtı ve transaction davranışı pazaryeri senaryolarına daha uygun; ekip deneyimi de bu tarafta. |
| MongoDB veya başka doküman veritabanı | Sipariş-kalem-gönderim-ödeme ilişkileri ve para tutarlılığı ilişkisel modeli ve gerçek transaction'ı gerektiriyor. |
| Geliştirmede H2, üretimde PostgreSQL | İki motor iki davranış demek; testin yeşil olması üretimin çalıştığını göstermiyor ([ADR-005](005-test-strategy.md)). |

## Consequences

### Olumlu
- Şema değişimi sürümlenmiş, gözden geçirilebilir ve tekrarlanabilir.
- Entity ile şema arasındaki sapma uygulama açılışında yakalanıyor (`validate`).
- Geliştirme, test ve üretim aynı motorda: "benim makinemde çalışıyordu" sınıfı hatalar azalıyor.

### Olumsuz
- Her model değişikliği elle SQL yazmak demek; JPA'nın otomatik şeması kadar hızlı değil.
- Migration sırası ve çakışması dallar arasında dikkat gerektiriyor (aynı numarayı iki dalda kullanmamak).

### Nötr / Trade-off
- Testler Testcontainers ile gerçek PostgreSQL kaldırıyor: daha yavaş ama anlamlı testler ([ADR-005](005-test-strategy.md)).

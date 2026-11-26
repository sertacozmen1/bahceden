# [ADR-113] Redis ve Kafka geliştirmede compose servisidir, testlerde Testcontainers ile gerçek broker'dır

**Tarih:** 2026-11-26
**Durum:** Accepted · change `add-redis-cache-and-order-paid-event` D7 · [ADR-005](005-test-strategy.md)'in "H2 ile sapma kabul edilmez" kuralını Redis ve Kafka'ya uygular

---

## Context

[ADR-107](107-redis-and-kafka-added-to-budget.md) Redis ve Kafka'yı bütçeye aldı. İkisinin de geliştirmede çalıştırılması ve testlerde doğrulanması gerekiyor.

Kısıtlar:

- Testler gerçek altyapıya karşı koşar ([ADR-005](005-test-strategy.md)). Lua betiği, TTL, `SKIP LOCKED` ile birlikte offset ve tüketici grubu davranışı taklit ortamında doğrulanamaz.
- Ayarlar tip güvenli okunur. Secret ve host kodda yoktur; eksik değerle uygulama açılmaz (K10).
- Testler veriyi çoğunlukla JDBC fixture'larıyla kurar: servisleri ve dolayısıyla cache silmesini atlar.
- Zamanlanmış işler testlerde kapalıdır; testler turları doğrudan çağırır.

## Decision

1. **Compose servisleri:**
   - `redis:7.2-alpine`, kalıcılık kapalı (`--save ""`, `--appendonly no`). Cache ve sayaçlar geçicidir; Redis yeniden başlarsa cache yeniden dolar, sayaç pencere başından sayar.
   - `apache/kafka:3.9.1`, KRaft tek düğüm (ZooKeeper yok). İki dinleyici açılır: `kafka:9092` konteyner ağı için, `localhost:9094` makineden izleme için. Otomatik topic açılışı kapalıdır, heap 512 MB ile sınırlıdır. Veri volume'a yazılmaz; olayın doğruluk kaynağı outbox tablosudur.
   - Her iki serviste healthcheck vardır; `api` ikisi de sağlıklı olunca açılır.
2. **Bağlantı ayarları:** Spring'in kendi `spring.data.redis.url` ve `spring.kafka.bootstrap-servers` anahtarları, varsayılansız ortam değişkeniyle okunur (`BAHCEDEN_REDIS_URL`, `BAHCEDEN_KAFKA_BOOTSTRAP`); `api/.env.example`'da belgelidir. Uygulamaya özgü ayarlar `BahcedenProperties.cache` ve `.outbox` altındadır ve doğrulanır.
3. **Testler:**
   - `TestcontainersConfig` PostgreSQL'in yanında Redis (`GenericContainer`, ayrı modül yok) ve Kafka (`org.testcontainers.kafka.KafkaContainer`) da açar. İmaj sürümleri compose ile aynıdır; bağlantılar `@ServiceConnection` ile gelir.
   - Relay zamanlayıcısı testlerde kapalıdır; Kafka dinleyicisi açıktır.
   - `CacheResetListener` (`spring.factories`), her test metodundan önce cache'leri boşaltır; veritabanı temizliğinin cache karşılığıdır.
   - Kesinti testleri bağlantıyı kapalı bir porta yönlendirir. Paylaşılan konteyner durdurulmaz.
4. **Uçtan uca doğrulama** compose üzerinde betikle yapılır (change `tasks.md` 5.1–5.2):
   - ödeme → topic → mailpit;
   - Kafka durdurulup ödeme yapılır, olay bekler, broker dönünce e-posta bir kez gider;
   - Redis durdurulur, vitrin ve giriş çalışmaya devam eder.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `spring-kafka-test` `EmbeddedKafka` | Gerçek broker değil; ek paket; KRaft davranışı ve topic yönetimi farklı |
| Testlerde Redis/Kafka'yı mock'lamak | Lua betiği, TTL, offset ve DLT doğrulanmaz; yalancı yeşil |
| Kafka verisini volume'a yazmak | Outbox zaten doğruluk kaynağı; volume izinleri (`appuser`) ek karmaşıklık |
| Kesinti testinde paylaşılan konteyneri durdurmak | Aynı bağlamı kullanan diğer testleri kırar; sıra bağımlılığı yaratır |
| Testlerde cache'i kapatmak (`spring.cache.type=none`) | Cache davranışı testte hiç sınanmaz; üretimden sapma |

## Consequences

### Olumlu
- Testler ve geliştirme, üretimdeki altyapıyla aynı motorlara karşı koşar.
- Kesinti davranışı (fail-open cache ve sayaç, bekleyen olay) hem testte hem compose'da doğrulandı.

### Olumsuz
- Kafka yerelde ~600 MB bellek ister; `docker compose up` ve CI `api` job'u yavaşlar (bağlam başına bir Kafka ve Redis konteyneri daha).
- Redis kesintisinde her vitrin isteği ve giriş, komut zaman aşımı kadar gecikir (ölçüldü: liste ~1 sn, giriş ~0,7 sn).
- Testler e-posta ve olay iddialarında tüketici grubunun offset'ini beklemek zorundadır.

### Nötr / Trade-off
- Üretim kurulumu (yönetilen Redis/Kafka, kopya sayısı, izleme, yedek) bu kararın kapsamı dışındadır ve barındırma kararına (KALANLAR B5) bağlıdır.

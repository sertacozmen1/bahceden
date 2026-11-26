# [ADR-107] Redis ve Kafka bütçeye bilinçli olarak eklenir: dağıtık hız sınırı, vitrin cache'i ve outbox ile iş olayları

**Tarih:** 2026-11-26
**Durum:** Accepted (proje sahibi onayı 2026-11-26) · change `add-redis-cache-and-order-paid-event` D1 · [ADR-002](002-dependency-budget.md) bütçesini genişletir · `AGENTS.md` §3'e sınırlı bir istisna ekler

---

## Context

- `AGENTS.md` §3 MVP için "event bus, kuyruk" gibi bugün gerekmeyen soyutlamaları yasaklar. [ADR-002](002-dependency-budget.md) bütçesinde Redis ve Kafka yoktur. [ADR-022](022-in-memory-fixed-window-rate-limiting.md) Redis tabanlı sayacı "yatay ölçekleme geldiğinde ADR ile gelir" diyerek reddetmiştir.
- İki somut teknik ihtiyaç ortaya çıktı:
  1. **Hız sınırı tek örneklidir** (`00-current-state.md` L9). Sayaçlar süreç belleğindedir. İki `api` örneğinde sınır iki katına çıkar, yeniden başlatma da sayaçları sıfırlar. Giriş, kayıt ve parola sıfırlama uçları bu sınıra güvenir.
  2. **Sipariş ödendi e-postası kaybolabilir.** `CheckoutService.settle()` e-postaları `afterCommit` ile gönderir. Süreç commit ile gönderim arasında düşerse e-posta yeniden denenmez. Ödenen sipariş ileride başka tüketicilerin (hakediş, analitik) de dinleyeceği bir iş olayıdır.
- Ayrıca vitrin uçları (ürün detayı, liste) kimliksizdir, en çok okunan uçlardır ve nadiren değişir. Kısa ömürlü bir okuma cache'i DB yükünü azaltır.
- **Açıkça yazılan ikinci gerekçe:** Bahçeden bir portföy projesidir. Java ekosisteminde yaygın iki altyapıyı, cache ile dağıtık sayacı ve olay akışını, gerçek bir iş akışında ve doğru desenlerle (transactional outbox, idempotent tüketici, fail-open cache) göstermek proje sahibinin bilinçli hedefidir. Bu gerekçe tek başına yeterli sayılmaz. Kullanım yerleri yukarıdaki gerçek ihtiyaçlarla sınırlıdır.

## Decision

1. **Bütçe genişler (ADR-002 değişim tablosu):**

   | Katman | Eklenen | Sürüm |
   |---|---|---|
   | `api/` runtime | `spring-boot-starter-data-redis` (Lettuce istemcisi) | Spring Boot BOM |
   | `api/` runtime | `spring-boot-starter-cache` | Spring Boot BOM |
   | `api/` runtime | `spring-kafka` | Spring Boot BOM |
   | `api/` test | `org.testcontainers:kafka` | Testcontainers BOM (Spring Boot yönetir) |
   | Sistem | `redis:7.2-alpine` | sabit etiket |
   | Sistem | `apache/kafka:3.9.1` (KRaft, tek düğüm; ZooKeeper yok) | sabit etiket |

   Redis testlerde `GenericContainer` ile çalışır, ayrı Testcontainers modülü alınmaz.

2. **Kullanım yerleri sınırlıdır.** Redis yalnızca dağıtık hız sınırı (ADR-108) ve herkese açık vitrin okumalarının cache'i (ADR-109) için kullanılır. Kafka yalnızca transactional outbox üzerinden yayınlanan iş olayları için kullanılır (ADR-110…112); ilk ve tek olay `ORDER_PAID`'dir. Yeni bir cache, yeni bir olay veya Redis/Kafka'nın başka bir kullanımı (oturum, sepet, kuyruk olarak iş dağıtımı) yine bir change ve ADR gerektirir.

3. **`AGENTS.md` §3 korunur ve tek istisna eklenir:** over-engineering yasağı aynen geçerlidir. Redis ve Kafka, 2. maddedeki kullanım yerleriyle sınırlı olarak bütçededir. Mikroservis ayırmak, olayları süreçler arası iş dağıtımı için kullanmak, schema registry ve Kafka Streams yasağın kapsamında kalır.

4. **Bağımlılık yönü değişmez.** Redis ve Kafka'ya yalnızca `api/` konuşur. `web/` bu servisleri bilmez (§3: dış servisler yalnızca `api/` üzerinden çağrılır).

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Hiç eklememek (bugünkü durum) | L9 açık kalır, sipariş e-postası kaybolabilir, portföy hedefi karşılanmaz |
| Kafka yerine PostgreSQL `LISTEN/NOTIFY` veya yalnızca outbox tablosu | Yeni paket gerektirmez, ama kalıcı olay log'u, tüketici grubu ve yeniden oynatma yoktur. Hedeflenen altyapı gösterilmez |
| Kafka yerine RabbitMQ | Kuyruk semantiği sunar. Olay log'u ve yeniden oynatma doğal değildir. Hedeflenen iş ilanlarında Kafka istenir |
| Redis yerine Caffeine (süreç içi cache) | Servis istemez, ama çok örnekte tutarsızdır ve hız sınırı sorununu çözmez |
| Valkey 8 (`valkey/valkey`) | Protokol uyumludur ve BSD lisanslıdır. Proje sahibi "Redis" adını ve son BSD lisanslı sürümü (7.2) seçti. Geçiş ileride yalnızca imaj değişikliğidir |
| Redis 7.4+ / 8 | Lisans RSAL/SSPL (7.4) ya da AGPL seçeneklidir (8). 7.2'nin özellikleri bu kullanım için yeterlidir |
| Confluent imajı (`confluentinc/cp-kafka`) | Daha büyük imaj, Confluent Community lisansı. Apache imajı resmidir ve yeterlidir |
| Bucket4j (hız sınırı kütüphanesi) | Bütçe dışı ek paket. Sabit pencere tek Lua betiğiyle yazılır |

## Consequences

### Olumlu
- L9 kapanır (ADR-108): sınır örnekler arasında paylaşılır ve yeniden başlatmada korunur.
- Sipariş ödendi olayı kalıcıdır. Kafka kapalıyken de kaybolmaz, yalnızca gecikir (ADR-110).
- Vitrin okumalarında DB yükü azalır (ADR-109).
- Kullanım yerleri ve sınırları yazılıdır. "Neden burada?" sorusunun cevabı bu ADR'dir.

### Olumsuz
- İki yeni servis: işletim, izleme ve yedek yükü getirir. Üretim kurulumu barındırma kararına (KALANLAR B5) eklenir.
- Kafka yerelde ~600 MB bellek ister. `docker compose up` ve CI `api` job'u (Testcontainers) yavaşlar.
- Sipariş e-postası relay aralığı ve tüketici gecikmesi kadar geç gider (birkaç saniye).
- Transitive yük: Lettuce + Netty (Redis), `kafka-clients` (~5 MB) gelir. Bunlar güvenlik yamalarının takip edileceği yeni yüzeylerdir.

### Nötr / Trade-off
- Redis ve Kafka kesintisi uygulamayı durdurmaz: hız sınırı ve cache fail-open çalışır, olaylar outbox'ta bekler. Kesinti sırasında koruma örnek başına çalışır, e-postalar gecikir.
- Portföy gerekçesi açıkça yazıldı. İleride ihtiyaç ortadan kalkarsa bu ADR *superseded* edilip servisler kaldırılabilir. Kaldırma yolu kısadır: hız sınırının bellek içi yedeği ve outbox tablosu zaten vardır.

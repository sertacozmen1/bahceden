# Design: add-redis-cache-and-order-paid-event

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2): D1 → ADR-107, D2 → ADR-108, D3 → ADR-109, D4 → ADR-110, D5 → ADR-111, D6 → ADR-112, D7 → ADR-113.
> Çerçeve:
> - [ADR-002](../../../docs/adr/002-dependency-budget.md): bağımlılık bütçesi. D1 bütçeyi genişletir; ADR-002'ye "ADR-107 ile genişledi" notu düşülür.
> - [ADR-022](../../../docs/adr/022-in-memory-fixed-window-rate-limiting.md): bellek içi sabit pencere. D2 bunu *superseded* eder; pencere semantiği ve politikalar aynen kalır.
> - [ADR-019](../../../docs/adr/019-email-via-smtp-provider-agnostic.md): e-posta hatası akışı kesmez. Aynen geçerli; tüketicide de gönderim hatası işlemi geri almaz.
> - [ADR-066](../../../docs/adr/066-payment-redirect-verify-lock-stock-on-success.md): stok ödeme anında kilitli okumayla düşer. Cache bu kararı etkilemez.
> - [ADR-007](../../../docs/adr/007-query-level-ownership-scoping.md): sahiplik sorgu seviyesinde. Cache yalnızca herkese açık vitrin verisine uygulanır.

## Kararlar

### D1 · Anayasa ve bütçe: Redis ve Kafka bilinçli olarak eklenir

- `AGENTS.md` §3'teki over-engineering cümlesi korunur ve şu istisna eklenir: "Redis (cache, dağıtık hız sınırı) ve Kafka (transactional outbox ile iş olayları) ADR-107 ile bütçededir; yeni olay veya cache kullanımı yine change gerektirir."
- K7 tablosu ve ADR-002 bütçesi: `spring-boot-starter-data-redis`, `spring-boot-starter-cache`, `spring-kafka` (runtime); `org.testcontainers:kafka` (test); `redis:7.2-alpine`, `apache/kafka:3.9.1` (sistem). Sürümler Spring Boot BOM'undan gelir. İmajlar sabit etiketlidir.
- §7'ye yeni geliştirme komutu: olay izleme (`kafka-console-consumer`), how-to'ya bağlanır.

**Gerekçe:**
- İki yerde gerçek bir teknik ihtiyaç var: L9 (çok örnekli rate limit) ve commit sonrası kaybolabilen e-posta.
- Proje bir portföy projesidir. Yaygın iki altyapıyı doğru desenle (outbox, idempotent tüketici, fail-open cache) göstermek bu kararın açıkça yazılan ikinci gerekçesidir. Sırf "ileride lazım olur" diye eklenmiyor; kullanım yeri ve sınırı bu change'te tanımlı.

**Reddedilen alternatifler:**

| Alternatif | Ret gerekçesi |
|---|---|
| Hiç eklememek (bugünkü durum) | L9 açık kalır; sipariş e-postası kaybolabilir; portföy hedefi karşılanmaz |
| Kafka yerine PostgreSQL `LISTEN/NOTIFY` veya yalnızca outbox tablosu | Yeni paket gerektirmez, ama tekrar oynatma, tüketici grubu ve kalıcı log yoktur; hedeflenen altyapı gösterilmez |
| Kafka yerine RabbitMQ | Kuyruk semantiği; olay log'u ve yeniden oynatma Kafka kadar doğal değil; iş ilanlarında hedef Kafka |
| Redis yerine Caffeine (süreç içi cache) | Yeni servis istemez ama çok örnekte tutarsızdır ve rate limit sorununu çözmez |
| Valkey 8 | Protokol uyumlu ve BSD lisanslı; proje sahibi "Redis" adını ve 7.2 BSD sürümünü seçti. Geçiş ileride imaj değişikliğidir |
| Redis 7.4+ / 8 | Lisans RSAL/SSPL (7.4) veya AGPL seçeneği (8); 7.2 yeterli |

### D2 · Rate limit: Redis'te atomik sabit pencere, Redis yoksa süreç içi yedek

- `RateLimiter.check(policy, keyPart...)` imzası ve `RATE_LIMITED` hatası değişmez. Çağıran kod değişmez.
- Anahtar: `rl:{policy}:{sha256(keyParts)}`. Düz e-posta/IP Redis'e yazılmaz (K10).
- Sayaç tek Lua betiğiyle tutulur: `INCR`; değer 1 ise `PEXPIRE window`; dönüş değeri sayaçtır. Sayaç ve süre atomik olarak ayarlanır; iki örnek aynı pencereyi paylaşır.
- Redis hatası (`RedisConnectionFailureException` vb.) yakalanır ve **açıkça modellenir**: `WARN` log'u yazılır (dakikada en çok bir kez) ve bugünkü bellek içi sayaç (`InMemoryWindowCounter`) aynı politikayla kullanılır. İstek reddedilmez.
- Bellek içi sayaç bugünkü `RateLimiter` kodudur. Ayrı bir sınıfa taşınır, yeniden yazılmaz (K9).

**Gerekçe:** Sabit pencere ADR-022 ile aynı semantiktir; testler ve politikalar değişmez. Fail-open seçildi, çünkü Redis kesintisi giriş ve kayıt uçlarını tümden kapatmamalı. Yedek sayaç, kesinti sırasında da örnek başına koruma sağlar.

**Reddedilen alternatifler:** (a) Kayan pencere veya token bucket: semantik değişir, testler yeniden yazılır, MVP'de fark yok. (b) Fail-closed (Redis yoksa 429): Redis kesintisi tüm girişleri durdurur. (c) Bucket4j: bütçe dışı ek paket.

### D3 · Vitrin cache'i: Spring Cache + Redis, kısa TTL, commit sonrası silme

- Cache adları ve anahtarları:
  - `storefront:product` → anahtar `slug`, değer `ProductDetail`;
  - `storefront:list` → anahtar `page:size`, yalnızca `page < 3` (ilk üç sayfa) cache'lenir (`condition`).
- TTL 60 sn (`bahceden.cache.storefront-ttl`). Değer JSON (Jackson, tip bilgisiyle) saklanır.
- **Silme:** yeni `StorefrontCacheEvictor`, transaction `afterCommit` sırasında çalışır. Bu desen `OrderNotifier.sendAfterCommit` ile aynıdır.
  - Ürüne özgü yazmalarda o ürünün `slug` kaydı ve `storefront:list` içeriği tamamen silinir;
  - satıcı durum kararı (`SellerService.decide`: onay, red, askıya alma) o satıcının ürünlerinin `slug`'larını ve listeyi siler;
  - stok değişiminde (`decreaseStock`, `increaseStock`) etkilenen ürünler silinir.
- Cache hatası okumayı kırmaz: `CacheErrorHandler` hatayı `WARN` ile loglar ve DB'den okur.
- Cache'lenmez: hesap, sepet, sipariş, satıcı paneli, admin uçları, `Accept-Language`'e bağlı metin üreten yanıtlar. Vitrin DTO'larında dile bağlı alan yok; `ProductDetail` ve `ProductSummary` bu yüzden dilden bağımsız cache'lenebilir. Bu, uygulamada testle sabitlenir.

**Gerekçe:** En çok okunan ve kimliksiz uçlar burasıdır. Fiyat ve stok kararı checkout'ta kilitli okumayla verilir (ADR-066), dolayısıyla en kötü durumda vitrinde 60 sn eski stok görünür ve para hatası doğmaz. Silme commit sonrasında yapılır; geri alınan transaction cache'i boşa silmez, commit öncesi okuma da eski değeri yeniden yazamaz.

**Reddedilen alternatifler:** (a) Uzun TTL ile yalnızca TTL'ye güvenmek: satıcı fiyat değiştirince dakikalarca eski fiyat görünür. (b) Write-through, yani DTO'yu yazmada güncellemek: DTO üretimi fotoğraf ve satıcı birleşimi ister ve yazma yolunu ağırlaştırır. (c) HTTP cache (`Cache-Control`) ve Next.js ISR: değerli ama ayrı bir karar; Redis hedefini karşılamaz.

### D4 · Transactional outbox: olay iş verisiyle aynı transaction'da yazılır

- Yeni tablo `outbox_events`:

  | Kolon | Tip | Not |
  |---|---|---|
  | `id` | `BIGSERIAL` PK | sıralama |
  | `event_id` | `UUID` unique | tüketici idempotency anahtarı |
  | `event_type` | `VARCHAR(64)` | `ORDER_PAID` |
  | `aggregate_key` | `VARCHAR(64)` | Kafka anahtarı (`orderNumber`) |
  | `payload` | `JSONB` | olay gövdesi |
  | `occurred_at` | `TIMESTAMPTZ` | `Clock` ile |
  | `published_at` | `TIMESTAMPTZ NULL` | relay işaretler |
  | `attempts` | `INT` | başarısız deneme sayısı |
  | `created_at`, `updated_at` | `TIMESTAMPTZ` | §11.4 |

  Kısmi indeks: `WHERE published_at IS NULL` (`id` üzerinde).
- `CheckoutService.settle()`, `order.markPaid(now)` ile aynı transaction'da `OutboxPublisher.append(OrderPaidEvent)` çağırır. `notifier.orderPaid(...)` çağrısı buradan kalkar.
- `OutboxRelay.relayOnce()` her `bahceden.outbox.interval` (2 sn) aralığında şunu yapar:
  - `SELECT … WHERE published_at IS NULL ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED` ile satırları alır;
  - her satırı `KafkaTemplate.send(topic, key, payload).get(timeout)` ile gönderir;
  - başarılı satırı `published_at` ile işaretler;
  - hata durumunda `attempts++` yapar, `WARN` yazar ve o turu bitirir (sıra korunur).
  - `SKIP LOCKED` sayesinde iki örnek aynı satırı göndermez.
- Zamanlama `PendingPaymentSweepSchedule` deseniyle kurulur: `@ConditionalOnProperty(bahceden.outbox.enabled)`. Testlerde kapalıdır, testler `relayOnce()` metodunu doğrudan çağırır.
- Yayınlanmış satırlar 7 gün sonra mevcut temizlik işine (`ExpiredTokenCleanup` deseni) eklenen bir adımla silinir.
- Producer ayarları: `acks=all`, `enable.idempotence=true`.

**Gerekçe:** `KafkaTemplate.send` doğrudan transaction içinde veya `afterCommit` sırasında çağrılırsa dual-write sorunu doğar: DB commit olur ama mesaj gitmez, ya da tersi. Outbox, olayın sipariş durumuyla **atomik** olarak kalıcı olmasını sağlar. Kafka'nın kapalı olması siparişi etkilemez.

**Reddedilen alternatifler:** (a) `afterCommit` sırasında doğrudan `send`: süreç düşerse olay kaybolur. (b) Kafka transaction'ı + `ChainedTransactionManager`: deprecated, gerçek atomiklik vermez. (c) Debezium CDC: ayrı servis ve Kafka Connect gerektirir, MVP için ağır.

### D5 · Olay sözleşmesi: `bahceden.order.paid.v1`, kişisel veri yok

- Topic `bahceden.order.paid.v1`, 3 bölüm (yerelde), anahtar `orderNumber` (aynı siparişin olayları sıralı).
- Gövde (`OrderPaidEvent` record, JSON):

  ```json
  {
    "eventId": "0b7c…",
    "eventType": "ORDER_PAID",
    "occurredAt": "2026-11-26T10:15:30Z",
    "orderId": 42,
    "orderNumber": "BHC-FH5M426922",
    "sellerOrderIds": [101, 102],
    "grandTotal": { "amount": 25550, "currency": "TRY" }
  }
  ```

- Para ADR-006'ya uygundur (minor unit + currency). Zaman UTC ISO-8601 biçimindedir.
- Kırıcı değişiklik yeni sürümlü topic (`.v2`) ile yapılır; alan eklemek kırıcı değildir. Olay kataloğu `docs/reference/` altında yeni bir sayfada tutulur (`openapi.yaml` HTTP sözleşmesidir; olay sözleşmesi buraya girmez).

**Gerekçe:** Tüketici ihtiyaç duyduğu kişisel veriyi DB'den, yetkili olarak okur. Topic ve log'lar böylece KVKK kapsamı dışında kalır (K10). Sürüm topic adındadır, çünkü schema registry kapsam dışıdır.

**Reddedilen alternatifler:** (a) Alıcı e-postasını ve dilini gövdeye koymak: kişisel veri topic'e ve broker diskine girer. (b) Avro + schema registry: yeni servis ve paket gerektirir.

### D6 · Tüketici: idempotent e-posta gönderimi

- `OrderPaidEmailConsumer`: `@KafkaListener(topics = "bahceden.order.paid.v1", groupId = "bahceden-order-mail")`.
- Olay işlenirken tek transaction'da şu adımlar yürür:
  1. `processed_events` (`consumer`, `event_id`) PK'sine `INSERT … ON CONFLICT DO NOTHING` yapılır; satır eklenmediyse olay zaten işlenmiştir ve atlanır;
  2. sipariş ve satıcı siparişleri ID ile okunur;
  3. `OrderNotifier.orderPaid(order, sellerOrders)` çağrılır (mevcut kod; K9). E-postalar `afterCommit` ile gider.
- Offset commit, listener başarıyla döndükten sonra yapılır (`AckMode.RECORD`). DB hatasında `DefaultErrorHandler` 3 kez geri çekilerek dener, sonra olayı `bahceden.order.paid.v1.DLT` topic'ine yollar ve `ERROR` log'u yazar.
- E-posta gönderim hatası ADR-019 gereği akışı kesmez. Gönderim yeniden denenmez; davranış bugünküyle aynıdır.

**Gerekçe:** En az bir kez teslimde tekrar beklenen bir durumdur. `processed_events` ile e-posta pratikte bir kez gider. Kalan dar pencere kabul edilir: commit olmuş, `afterCommit` gönderimi sürerken süreç düşmüş olabilir ve `processed_events` satırı da commit olmuştur. Bu durumda e-posta gitmeyebilir, ama bugün de aynı pencere vardır. Kötü durum, çift e-posta yerine eksik e-postadır ve bu bugünkü davranışla eşittir.

**Reddedilen alternatifler:** (a) Idempotency'yi Redis'te tutmak (`SETNX`): TTL'ye bağlıdır ve DB ile atomik değildir. (b) Kafka exactly-once: yalnızca Kafka→Kafka zincirinde geçerlidir, e-posta yan etkisini kapsamaz.

### D7 · Geliştirme ortamı ve test

- `docker-compose.yml`:
  - `redis` (`redis:7.2-alpine`, healthcheck `redis-cli ping`, kalıcılık kapalı: cache ve sayaç geçicidir);
  - `kafka` (`apache/kafka:3.9.1`, KRaft tek düğüm, healthcheck `kafka-topics.sh --list`).
  - `api` her ikisine `service_healthy` ile bağlanır.
  - Topic'ler uygulama açılışında `NewTopic` bean'leriyle oluşturulur.
- Ayarlar `@ConfigurationProperties` ile okunur (`BAHCEDEN_REDIS_URL`, `BAHCEDEN_KAFKA_BOOTSTRAP`, `BAHCEDEN_OUTBOX_ENABLED`, `BAHCEDEN_OUTBOX_INTERVAL`, `BAHCEDEN_CACHE_STOREFRONT_TTL`) ve `api/.env.example`'a açıklamasıyla eklenir. Üretim profilinde eksik değer açılışı düşürür (K10 fail-fast).
- Testler:
  - mevcut test tabanına Testcontainers Redis (`GenericContainer`) ve Kafka (`KafkaContainer`) eklenir, singleton olarak test başına bir kez başlar;
  - testler gerçek broker'a karşı koşar, sahte sınıf yoktur. Bu, H2 yasağının olay karşılığıdır (ADR-005).

**Gerekçe:** Mevcut desenlere uyulur: Postgres için Testcontainers, zamanlama için `ConditionalOnProperty`, ayarlar için tip güvenli properties.

**Reddedilen alternatifler:** (a) `spring-kafka-test` `EmbeddedKafka`: gerçek broker değildir ve ek paket ister. (b) Testlerde Redis/Kafka'yı mock'lamak: Lua betiği, TTL ve offset davranışı doğrulanmaz.

## Yetkilendirme ve kişisel veri

- Yeni HTTP ucu yok; yetkilendirme yüzeyi değişmez.
- Cache yalnızca herkese açık vitrin yanıtlarını tutar. Görünmeyen ürün (taslak, kaldırılmış, satıcısı onaysız) cache'te **bulunamaz**, çünkü 404 sonucu cache'lenmez (`unless = "#result == null"`, istisna fırlatıldığında da cache'e yazılmaz).
- Olay gövdesi ve Redis anahtarları kişisel veri taşımaz (D2, D5).

## Test etkisi (§6.3)

- **`critical`, rate limit (Testcontainers Redis, sabit `Clock`):**
  - iki ayrı `RateLimiter` örneği aynı Redis'i paylaşır, sınır toplamda uygulanır;
  - pencere dolunca yeniden kabul edilir;
  - Redis durdurulunca istek reddedilmez ve yedek sayaç sınırı örnek başına uygular;
  - Redis anahtarında düz e-posta yoktur.
- **`critical`, cache:**
  - ürün detayı ikinci okumada DB'ye gitmez (sorgu sayısı testi);
  - ilan güncellendiğinde, ürün kaldırıldığında, satıcı askıya alındığında, stok düştüğünde sonraki okuma tazedir;
  - geri alınan transaction cache'i silmez;
  - görünmeyen ürün 404 döner ve cache'lenmez;
  - Redis yokken okuma DB'den çalışır.
- **`critical`, outbox:**
  - başarılı ödeme aynı transaction'da tek `ORDER_PAID` satırı yazar;
  - başarısız, süresi dolmuş veya stoksuz ödeme satır yazmaz;
  - geri alınan transaction satır bırakmaz;
  - relay gönderir ve işaretler;
  - Kafka kapalıyken satır bekler ve broker dönünce gönderilir;
  - iki relay aynı satırı göndermez (`SKIP LOCKED`).
- **`critical`, tüketici:**
  - olay alıcı ve satıcı e-postalarını gönderir (mailpit/bellek `MailSender`);
  - aynı olay iki kez gelirse e-posta bir kez gider;
  - işlenemeyen olay 3 denemeden sonra DLT'ye düşer.
- **Mevcut testler:** başarılı ödeme senaryosundaki "alıcıya ve satıcıya e-posta gider" iddiaları artık relay ve tüketici adımını bekler (`relayOnce()` + Kafka poll). Gevşetilmez; aynı iddia olay yolundan doğrulanır. `RateLimiter` testleri yedek sayaç için aynen korunur.
- **Regression riski:** checkout transaction'ı bir INSERT uzar. Giriş ve kayıt uçlarına Redis gidiş-dönüşü eklenir (~1 ms). E-posta zamanlamasına bağlı web testleri yoktur.

## Migration Plan

1. Changeset 017 eklenir (yalnızca yeni tablolar; geri alma `DROP TABLE`).
2. Faz sırası: altyapı (compose + bağımlılık) → rate limit → cache → outbox + relay → tüketici, ardından `OrderNotifier` çağrısının taşınması. Taşıma tek commit'te yapılır; iki e-posta yolu aynı anda çalışmaz (§15 "iki alternatif birlikte" yasağı).
3. Geri dönüş: `bahceden.outbox.enabled=false` relay'i durdurur, olaylar birikir. Tam geri dönüş için ayrı bir change gerekir.

## Open Questions

- Yok. Redis kullanım alanı, tüketici ve imaj proje sahibiyle 2026-11-26'da karara bağlandı. Üretimde yönetilen Redis/Kafka seçimi B5'e (barındırma) bağlıdır ve `KALANLAR.md` B5 maddesine not olarak düşülür.

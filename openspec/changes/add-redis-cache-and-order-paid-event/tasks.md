# Tasks: add-redis-cache-and-order-paid-event

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni bağımlılık **var** (K7): proposal "Bağımlılık" tablosu; proje sahibi onayı olmadan Faz 0 başlamaz.
> Şema değişir: changeset `017` (yalnızca yeni tablolar).

## Faz 0 · Karar ve altyapı (D1, D7)

- [x] 0.1 ADR-107 (D1) yazılır; ADR-002'ye "ADR-107 ile genişledi" notu; `AGENTS.md` §3 istisnası, K7 tablosu, §2 haritası (`docker-compose.yml` satırı), §7 komutları; `docs/adr/index.md` + `docs/.tools/mkdocs.yml`. (§7'de yalnızca `docker compose up` satırına portlar eklendi; olay izleme komutu how-to ile Faz 5'te gelir.)
- [x] 0.2 `api/pom.xml`: `spring-boot-starter-data-redis`, `spring-boot-starter-cache`, `spring-kafka`, test `org.testcontainers:kafka` (BOM'dan pin'li).
- [x] 0.3 `docker-compose.yml`: `redis` (`redis:7.2-alpine`) ve `kafka` (`apache/kafka:3.9.1`, KRaft) servisleri, healthcheck; `api` bağımlılıkları ve ortam değişkenleri.
- [x] 0.4 Bağlantı ayarları: `application.yaml`'da Spring'in kendi `spring.data.redis.url` ve `spring.kafka.bootstrap-servers` anahtarları, varsayılansız ortam değişkeniyle (`BAHCEDEN_REDIS_URL`, `BAHCEDEN_KAFKA_BOOTSTRAP`; eksikse açılış düşer); `application-test.yaml` yer tutucuları; `api/.env.example`. (Plandan sapma: `BahcedenProperties`'e `outbox` ve `cache` bölümleri bu fazda **eklenmedi**; kullanılmayan ayar K6'ya göre yer tutucu olurdu. Her bölüm kendi fazında, onu okuyan kodla gelir: `cache` Faz 2, `outbox` Faz 3.)
- [x] 0.5 Test tabanı: singleton Testcontainers Redis (`GenericContainer`) + Kafka (`KafkaContainer`); mevcut testler değişmeden yeşil. (`TestcontainersConfig`'e iki `@ServiceConnection` bean'i; imajlar compose ile aynı.)
- [x] 0.6 **Doğrulama:** `./mvnw -B verify` temiz (JDK 21; 359 test, 0 hata); `docker compose up -d --build --wait` ile `db`, `redis`, `kafka`, `mailpit`, `api` healthy, `GET /api/v1/health` → `UP`, `redis-cli ping` → `PONG`, `api` log'unda hata yok. (Değişken tanımsızken açılışın düşmesi ayrıca denenmedi.)

## Faz 1 · Dağıtık hız sınırı (D2)

- [x] 1.1 Bugünkü sayaç `InMemoryWindowCounter`'a taşınır (davranış aynı; mevcut testler bu sınıfa yönelir). (Sayaç arayüzü `WindowCounter`; mevcut testler gerçek Redis'e karşı aynen korunur.)
- [x] 1.2 `RedisWindowCounter`: tek Lua betiği, anahtar `rl:{policy}:{sha256}`. (Plandan sapma: `INCR` + `PEXPIRE` yerine pencere başı + sayı hash'i, "şimdi" uygulamanın `Clock`'undan; semantik ADR-022 ile aynı kalır ve sabit saatli testler çalışır. design.md D2 güncellendi.)
- [x] 1.3 `RateLimiter`: önce Redis, hata olursa `WARN` (dakikada bir) + yedek sayaç. İmza değişmez.
- [x] 1.4 **Test (`critical`):** design.md "Test etkisi · rate limit" listesi. (`RateLimiterTest`: 3 mevcut + 4 yeni test; iki örnek paylaşır, yeniden başlatma sıfırlamaz, anahtarda düz e-posta/IP yok, erişilemeyen Redis'te yedek sayaç sınırı uygular.)
- [x] 1.5 ADR-108; ADR-022 → *Superseded by ADR-108*; `00-current-state.md` L9 kalkar; `hesap-ve-oturum.md` Tarihçe.
- [x] 1.6 **Doğrulama:** `./mvnw -B verify`, `-Dgroups=critical`. (JDK 21; `verify` 363 test, 0 hata; `critical` grubu temiz.)

## Faz 2 · Vitrin cache'i (D3)

- [ ] 2.1 `common/cache/CacheConfig`: `@EnableCaching`, `RedisCacheManager` (JSON, TTL ayardan), `CacheErrorHandler` (logla + DB).
- [ ] 2.2 `ProductService.detail` / `listPublished`: `@Cacheable` (liste için `page < 3`; boş/404 cache'lenmez).
- [ ] 2.3 `catalog/StorefrontCacheEvictor` (`afterCommit`); çağrı yerleri: `ListingService` (update, photo ekle/sil/sırala, transition), `ProductModerationService` (remove, reinstate), `SellerService.decide`, `CheckoutService.settle` (stok düşmesi), `OrderCancellationService` (stok iadesi).
- [ ] 2.4 **Test (`critical`):** design.md "Test etkisi · cache" listesi (sorgu sayısı testi dahil).
- [ ] 2.5 ADR-109; `docs/reference/` mimari sayfası; `vitrin-ve-arama.md` Tarihçe.
- [ ] 2.6 **Doğrulama:** `./mvnw -B verify`; `OpenApiContractTest` sözleşmenin değişmediğini doğrular.

## Faz 3 · Outbox ve relay (D4, D5)

- [ ] 3.1 Changeset `017-outbox-and-processed-events.sql` (`--rollback`, kısmi indeks); master changelog include.
- [ ] 3.2 `common/outbox/`: `OutboxEvent` entity, `OutboxRepository` (`FOR UPDATE SKIP LOCKED`), `OutboxPublisher.append`, `OutboxRelay.relayOnce`, `OutboxRelaySchedule` (`@ConditionalOnProperty`); topic `NewTopic` bean'leri; producer `acks=all`, idempotent.
- [ ] 3.3 `order/OrderPaidEvent` record (D5 gövdesi); `CheckoutService.settle` başarılı ödemede `append` çağırır. **Bu fazda `notifier.orderPaid` çağrısı yerinde kalır**; olay yayınlanır ama henüz tüketilmez. Böylece faz tek başına çalışır ve e-posta yolu tektir.
- [ ] 3.4 Yayınlanmış outbox satırlarının 7 günlük temizliği mevcut temizlik işine adım olarak eklenir.
- [ ] 3.5 **Test (`critical`):** design.md "Test etkisi · outbox" listesi.
- [ ] 3.6 ADR-110, ADR-111; `docs/reference/` olay kataloğu sayfası (yeni) + ER diyagramı.
- [ ] 3.7 **Doğrulama:** `./mvnw -B verify`.

## Faz 4 · Tüketici ve e-postanın taşınması (D6)

- [ ] 4.1 `order/OrderPaidEmailConsumer`: `@KafkaListener`, `processed_events` ile idempotency, `OrderNotifier.orderPaid` çağrısı; `DefaultErrorHandler` (3 deneme + DLT).
- [ ] 4.2 `CheckoutService.settle` içindeki `notifier.orderPaid(...)` çağrısı **aynı commit'te** kaldırılır (tek e-posta yolu).
- [ ] 4.3 **Test (`critical`):** design.md "Test etkisi · tüketici" listesi; mevcut "başarılı ödeme e-postası" iddiaları olay yolundan doğrulanacak şekilde güncellenir (gevşetilmez).
- [ ] 4.4 ADR-112; `sepet-ve-siparis.md` Tarihçe.
- [ ] 4.5 **Doğrulama:** `./mvnw -B verify`, `-Dgroups=critical`.

## Faz 5 · Uçtan uca doğrulama ve doküman

- [ ] 5.1 `docker compose up -d --build`: sepet → ödeme → mailpit'te alıcı ve satıcı e-postaları; `kafka-console-consumer` ile topic'te olay (kişisel veri yok); Kafka durdurulup ödeme yapılır → e-posta beklemede; Kafka başlatılır → e-posta gelir, bir kez.
- [ ] 5.2 Vitrin: ürün detayı iki kez okunur (ikincisi cache), satıcı fiyatı günceller → sonraki okuma yeni fiyat; Redis durdurulur → vitrin ve giriş çalışmaya devam eder.
- [ ] 5.3 ADR-113 (D7); yeni how-to "Kafka olaylarını ve Redis'i yerelde izleme" + `how-to/index.md`; `how-to/yerel-gelistirme.md` (yeni servisler, bellek ihtiyacı).
- [ ] 5.4 `00-current-state.md` (servisler, test sayıları, bilinen sınırlamalar: e-posta gecikmesi, D6 dar penceresi); `KALANLAR.md` B5'e "yönetilen Redis/Kafka" notu; `_genel.md` Tarihçe; `features/index.md`; `README.md` mimari özeti.
- [ ] 5.5 Spec sync (delta → ana spec'ler); `./docs/.tools/serve.sh --build` temiz; `npm --prefix web run typecheck` (sözleşme değişmediği için tip farkı yok).

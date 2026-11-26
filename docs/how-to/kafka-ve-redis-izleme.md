# Nasıl Yapılır: Kafka Olaylarını ve Redis'i Yerelde İzleme

**Özet:** Bu rehber, `docker compose` ortamında sipariş ödendi olayını uçtan uca izlemeyi, outbox ve tüketici durumunu okumayı, vitrin cache'ini ve hız sınırı anahtarlarını görmeyi ve Kafka ile Redis kesintisini denemeyi anlatır. Kararlar: [ADR-107](../adr/107-redis-and-kafka-added-to-budget.md)…[ADR-113](../adr/113-redis-kafka-in-compose-and-real-brokers-in-tests.md). Olay sözleşmeleri: [Olay kataloğu](../reference/04-event-catalog.md).

## Ön Koşullar

- [Yerel geliştirme ortamı](yerel-gelistirme.md) kurulu ve `docker compose up -d --build --wait` ile ayakta (`db`, `redis`, `kafka`, `mailpit`, `api`, `web`).
- Dev hesapları ve parolası yerel geliştirme rehberindedir; burada tekrar edilmez.
- Bu komutlar yalnızca yerel veritabanına ve yerel broker'a dokunur. Gerçek müşteri verisi bu ortama konmaz (K10).

## Adımlar

### 1. Bir sipariş verin ve ödeyin

`alici@ornek.test` ile vitrinden `uretici@ornek.test`'in yayındaki bir ürününü sepete ekleyip ödemeyi tamamlayın ([yerel geliştirme](yerel-gelistirme.md) "Sipariş akışı"). Ödeme sahte sağlayıcıyla anında onaylanır. E-postalar ödeme anında değil, birkaç saniye sonra olay işlenince gider.

### 2. Outbox satırını okuyun

```bash
docker compose exec db psql -U bahceden -d bahceden -c \
  "select id, event_type, aggregate_key, published_at, attempts from outbox_events order by id desc limit 5;"
```

`published_at` doluysa relay olayı Kafka'ya göndermiştir (aralık 2 sn). Boş ve `attempts` artıyorsa broker'a ulaşılamıyordur.

### 3. Topic'i okuyun

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic bahceden.order.paid.v1 \
  --from-beginning --property print.key=true --timeout-ms 5000
```

Anahtar sipariş numarasıdır. Gövdede ad, e-posta veya adres **olmamalıdır** ([ADR-111](../adr/111-order-paid-event-contract-versioned-topic-no-pii.md)). Alan sırası kaynaktaki sırayla aynı olmayabilir: gövde PostgreSQL `jsonb` kolonundan okunur ve normalleştirilir.

### 4. Tüketici grubunu ve ölü mektupları okuyun

```bash
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group bahceden-order-mail

docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic bahceden.order.paid.v1.dlt \
  --from-beginning --timeout-ms 5000
```

`LAG` 0 ise tüketici bütün olayları işlemiştir. İşlenmiş olaylar `processed_events` tablosundadır. `.dlt`'deki kayıt otomatik yeniden oynatılmaz ([ADR-112](../adr/112-order-paid-emails-from-idempotent-consumer.md)).

### 5. Redis anahtarlarını görün

```bash
docker compose exec redis redis-cli --scan --pattern 'cache:*'
docker compose exec redis redis-cli --scan --pattern 'rl:*'
docker compose exec redis redis-cli ttl cache:storefront-product:<slug>
```

- `cache:storefront-product:<slug>` ve `cache:storefront-list:<sayfa>:<boyut>` anahtarları vitrin cache'idir; TTL 60 sn'dir ([ADR-109](../adr/109-storefront-read-cache-redis-evict-after-commit.md)).
- `rl:<POLİTİKA>:<sha256>` anahtarları hız sınırı sayaçlarıdır; e-posta ve IP düz yazılmaz ([ADR-108](../adr/108-rate-limit-counters-in-redis-fail-open.md)).

### 6. Kafka kesintisini deneyin

```bash
docker compose stop kafka
```

Bir sipariş daha verin ve ödeyin. Ödeme yine anında tamamlanır. Outbox satırı `published_at` boş kalır, `attempts` artar ve e-posta gitmez. Sonra:

```bash
docker compose start kafka
```

Broker açılınca relay olayı gönderir; alıcıya ve satıcıya **birer** e-posta mailpit'e düşer.

### 7. Redis kesintisini deneyin

```bash
docker compose stop redis
```

Vitrin, ürün detayı ve giriş çalışmaya devam eder; her istek komut zaman aşımı kadar (yaklaşık 0,5–1 sn) yavaşlar. `api` log'unda "Vitrin cache'i okunamadı" ve "Hız sınırı Redis'e ulaşamadı" uyarıları görünür. Sınır bu süre boyunca örnek başına uygulanır. Sonra:

```bash
docker compose start redis
```

## Olası Hatalar (Troubleshooting)

- **Hata:** `api` açılmıyor, log'da `Could not resolve placeholder 'BAHCEDEN_REDIS_URL'` veya `BAHCEDEN_KAFKA_BOOTSTRAP`.
  **Çözüm:** Değişkenin varsayılanı yoktur (K10). Compose dışında çalıştırıyorsanız `api/.env.example`'daki değerlerle tanımlayın (`redis://localhost:6379`, `localhost:9094`).
- **Hata:** `api` sağlıklı olmuyor; `kafka` healthcheck'i uzun sürüyor.
  **Çözüm:** Kafka ilk açılışta 20–40 sn sürebilir ve ~600 MB bellek ister. Docker'ın bellek sınırını kontrol edin: `docker compose ps`, `docker compose logs kafka`.
- **Hata:** Topic yok (`UNKNOWN_TOPIC_OR_PARTITION`).
  **Çözüm:** Otomatik topic açılışı kapalıdır; topic'leri `api` açılışta oluşturur. `api`'yi yeniden başlatın.
- **Hata:** Fiyatı değiştirdim ama vitrin eskisini gösteriyor.
  **Çözüm:** Değişiklik servis yolundan (satıcı paneli veya API) yapıldıysa cache commit sonrası silinir. Veritabanında doğrudan SQL ile yapılan değişikliği cache görmez; en çok 60 sn sonra tazelenir ya da `redis-cli --scan --pattern 'cache:*' | xargs redis-cli del` ile silinir.

## İlgili kararlar

[ADR-107](../adr/107-redis-and-kafka-added-to-budget.md) · [ADR-108](../adr/108-rate-limit-counters-in-redis-fail-open.md) · [ADR-109](../adr/109-storefront-read-cache-redis-evict-after-commit.md) · [ADR-110](../adr/110-transactional-outbox-relay-skip-locked.md) · [ADR-111](../adr/111-order-paid-event-contract-versioned-topic-no-pii.md) · [ADR-112](../adr/112-order-paid-emails-from-idempotent-consumer.md) · [ADR-113](../adr/113-redis-kafka-in-compose-and-real-brokers-in-tests.md)

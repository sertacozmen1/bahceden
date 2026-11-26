# add-redis-cache-and-order-paid-event

**Durum:** Önerildi (2026-11-26). Proje sahibinin onayı bekleniyor; onay gelmeden uygulama başlamaz (`AGENTS.md` K1, §5.3).

> Bu change **anayasayı bilinçli olarak genişletir**. `AGENTS.md` §3 bugün "event bus, kuyruk" eklemeyi yasaklar; K7 bütçesinde ([ADR-002](../../../docs/adr/002-dependency-budget.md)) Redis ve Kafka yoktur; [ADR-022](../../../docs/adr/022-in-memory-fixed-window-rate-limiting.md) Redis'i "yatay ölçekleme geldiğinde ADR ile gelir" diyerek reddetmiştir. Bu change'in ilk kararı (D1) bu üç kaydı açıkça günceller. Kararın gerekçesi sadece teknik değildir. Proje bir **portföy projesidir** ve Java ekosisteminde yaygın iki altyapıyı, cache ve olay akışını, gerçek bir iş akışı üzerinde ve sorumlu biçimde göstermek bilinçli bir hedeftir. Bu gerekçe ADR'de saklanmadan yazılır.

## Why

- **Rate limit tek örnekle sınırlı (L9, ADR-022).** `RateLimiter` sayaçları süreç belleğinde tutuyor. İki `api` örneği çalışırsa sınır iki katına çıkar. Yeniden başlatma da sayaçları sıfırlar. Giriş ve parola sıfırlama uçları ise bu sınıra güveniyor.
- **Vitrin her istekte veritabanına gidiyor.** Ürün detayı (`GET /api/v1/products/{slug}`) ve vitrin listesi kimlik istemez, en çok okunan uçlardır ve nadiren değişir. Kısa ömürlü bir okuma cache'i DB yükünü azaltır. Stok kararı zaten checkout'ta kilitli okumayla verildiği için ([ADR-066](../../../docs/adr/066-payment-redirect-verify-lock-stock-on-success.md)) cache'in eski veri göstermesi para veya stok hatasına yol açmaz.
- **"Sipariş ödendi" anı bugün bir metot çağrısı.** `CheckoutService.settle()` e-postaları `afterCommit` ile hemen gönderiyor. Süreç commit ile gönderim arasında düşerse e-posta kaybolur ve yeniden denenmez. Ödenen sipariş, ileride başka tüketicilerin de dinleyeceği bir iş olayıdır (hakediş, analitik, satıcı bildirimi). Bunu kalıcı ve yeniden oynatılabilir bir olay olarak yayınlamak hem kayıp e-posta sorununu çözer hem de doğru deseni (transactional outbox) kurar.

## What Changes

- **Anayasa ve bütçe (D1):** `AGENTS.md` §3 ve K7 tablosu güncellenir. ADR-002 bütçesine `spring-boot-starter-data-redis`, `spring-boot-starter-cache`, `spring-kafka` ve test için `org.testcontainers:kafka` eklenir. Sistem satırına `redis:7.2-alpine` ve `apache/kafka` (KRaft, tek düğüm) eklenir. ADR-022 *superseded* olur.
- **`api`, Redis rate limit (D2):** `RateLimiter` aynı `check(policy, keyPart...)` imzasıyla Redis'te atomik sabit pencere sayacı kullanır (`INCR` + ilk istekte `PEXPIRE`, tek Lua betiği). Politikalar (`RateLimitPolicy`) değişmez. Redis'e ulaşılamazsa istek **reddedilmez**, süreç içi yedek sayaçla devam edilir ve `WARN` log'u yazılır (fail-open, D2).
- **`api`, vitrin cache'i (D3):** Spring Cache soyutlaması ve Redis. `ProductService.detail(slug)` ve `listPublished(page, size)` için ilk sayfalar cache'lenir. TTL kısadır (60 sn). Ürünün vitrindeki görünümünü değiştiren her yazma işlemi (ilan güncelleme, fotoğraf, durum geçişi, admin kaldırma/geri alma, stok düşmesi/iadesi, satıcı onayı/askıya alma) commit sonrasında ilgili kayıtları siler. Redis yoksa cache atlanır ve okuma DB'den yapılır.
- **`api`, `OrderPaid` olayı (D4, D5, D6):**
  - `settle()` başarılı ödemede, aynı transaction'da `outbox_events` tablosuna bir `ORDER_PAID` satırı yazar;
  - zamanlanmış `OutboxRelay` gönderilmemiş satırları sırayla `bahceden.order.paid.v1` topic'ine yollar (anahtar `orderNumber`) ve gönderilenleri işaretler; teslim garantisi en az bir kez olur;
  - `OrderPaidEmailConsumer` olayı tüketir ve sipariş onayı ile satıcı e-postalarını gönderir. Bunlar bugün `OrderNotifier.orderPaid` ile gönderilen e-postalardır. Tüketici, işlediği olayı `processed_events` tablosuna yazarak tekrar gelen olayı yok sayar (idempotent).
  - Olay gövdesi kişisel veri taşımaz: olay kimliği, sipariş kimliği ve numarası, satıcı siparişi kimlikleri, toplam (`amount` + `currency`), zaman. E-posta adresi ve hesap dili tüketicide DB'den okunur (K10).
- **Geliştirme ortamı (D7):** `docker-compose.yml` dosyasına `redis` ve `kafka` servisleri eklenir (healthcheck'li). `api` bu servislere bağlanır. Testler Testcontainers ile gerçek Redis ve Kafka kullanır.
- **Sözleşme:** değişmez. Yeni uç yok, alan yok; cache ve olay `api`'nin iç işidir.

## Capabilities

- **Değişen:** `cart-and-checkout` · "Ödeme sağlayıcıdan doğrulanır; stok ödeme başarılı olunca düşer": e-postalar commit sonrası doğrudan değil, kalıcı `OrderPaid` olayıyla gider; olay kaybolmaz ve bir kez etkili işlenir.
- **Eklenen:** `storefront-catalog` · "Vitrin okumaları kısa süre cache'lenir ve değişiklikte tazelenir".
- **Eklenen:** `account-identity` · "Hız sınırı uygulama örnekleri arasında paylaşılır".

## Impact

| Katman | Etkilenir mi? | Ne değişir? |
|---|---|---|
| `api/` | Evet | `pom.xml` (3 runtime + 1 test bağımlılığı, Spring Boot BOM ile pin'li); `common/ratelimit/RateLimiter` (Redis + yedek); yeni `common/cache/CacheConfig`; `catalog/ProductService` (`@Cacheable`) + yeni `catalog/StorefrontCacheEvictor` (commit sonrası silme; `ListingService`, `ProductModerationService`, `CheckoutService`, `OrderCancellationService`, `SellerService` çağırır); yeni `common/outbox/` (`OutboxEvent`, `OutboxRepository`, `OutboxRelay`, `OutboxRelaySchedule`); `order/OrderPaidEvent` (record) + `order/OrderPaidEmailConsumer`; `OrderNotifier.orderPaid` artık tüketiciden çağrılır; `BahcedenProperties` (redis/kafka/outbox ayarları); `api/.env.example`; testler |
| `web/` | Hayır | — |
| DB | Evet | Yeni changeset `017-outbox-and-processed-events.sql`: `outbox_events`, `processed_events` (+ indeks, `--rollback`). Mevcut veri dönüşümü yok |
| `shared/` | Hayır | `openapi.yaml` değişmez; `OpenApiContractTest` bunu doğrular |
| `docs/` | Evet | ADR-107…113; ADR-002 ve ADR-022'ye not/superseded; `AGENTS.md` §3, K7, §7; `docs/reference/` (ER diyagramı, mimari, olay kataloğu sayfası); yeni how-to "Kafka olaylarını yerelde izleme"; `00-current-state.md` (L9 kalkar, yeni servisler); `sepet-ve-siparis.md`, `vitrin-ve-arama.md`, `hesap-ve-oturum.md`, `_genel.md` Tarihçe |
| Altyapı | Evet | `docker-compose.yml` (`redis`, `kafka`); `.github/workflows/ci.yml` değişmez (Testcontainers Docker'ı zaten kullanıyor), ama `api` job süresi artar |

## Bağımlılık (K7, onay gerekiyor)

| Paket / imaj | Katman | Neden | Sürüm |
|---|---|---|---|
| `spring-boot-starter-data-redis` (Lettuce) | `api` runtime | Redis istemcisi; Spring'in kendi paketi | Spring Boot 3.5.16 BOM |
| `spring-boot-starter-cache` | `api` runtime | `@Cacheable` soyutlaması | BOM |
| `spring-kafka` | `api` runtime | `KafkaTemplate`, `@KafkaListener` | BOM |
| `org.testcontainers:kafka` | `api` test | Gerçek broker'a karşı test (H2 kuralının olay karşılığı) | Testcontainers BOM |
| `redis:7.2-alpine` | Sistem | Son BSD lisanslı Redis sürümü | `7.2` sabit etiket |
| `apache/kafka:3.9.1` | Sistem | KRaft tek düğüm; ZooKeeper yok | sabit etiket |

Redis Testcontainers'ta `GenericContainer` ile çalışır; ayrı modül alınmaz. Alternatifler ve ret gerekçeleri `design.md` D1'dedir.

## Kişisel veri (K10)

- Olay gövdesinde ad, e-posta, adres, telefon **yok**; yalnızca kimlikler ve tutar. Topic'e yazılan veri bu yüzden KVKK kapsamında kişisel veri taşımaz.
- Cache'e yalnızca vitrin DTO'ları yazılır. Bunlar herkese açık veridir; hesap, sepet, sipariş cache'lenmez.
- Rate limit anahtarları e-posta içerebilir (`PASSWORD_RESET_REQUEST_EMAIL`). Redis'e anahtar olarak e-postanın **SHA-256 özeti** yazılır, düz e-posta yazılmaz.
- Log'a olay kimliği ve sipariş numarası yazılır; e-posta adresi yazılmaz.

## Non-goals

- Mikroservis ayırmak. Olayı üreten ve tüketen aynı `api` sürecidir; amaç dağıtık mimari değil, olayın güvenilir yayınlanmasıdır.
- Diğer e-postaları (iptal, kargo, ayıplı ürün) olaya taşımak. İlk dilim tek olayla sınırlıdır; desen oturunca ayrı change ile genişler.
- Sepet, oturum veya JWT'yi Redis'e taşımak. Oturum modeli ADR-008'dedir ve değişmez.
- Schema registry, Avro, Kafka Streams, exactly-once transaction'lar. Olay JSON'dur ve sürümü topic adında tutulur (`.v1`).
- Kafka Connect / Debezium ile CDC tabanlı outbox. Zamanlanmış relay MVP için yeterlidir.
- Üretim kurulumu (yönetilen Redis/Kafka, yedek, izleme). Bunlar B5 (barındırma) kararına bağlıdır.

## Riskler

- **Altyapı yükü:** iki yeni servis gelir. Kafka yerelde yaklaşık 600 MB bellek ister, `docker compose up` yavaşlar, CI `api` job'u Testcontainers yüzünden uzar. Bunlar D1'de bilinçli bedel olarak yazılır.
- **E-posta gecikmesi:** sipariş onayı artık relay aralığı (varsayılan 2 sn) ve tüketici gecikmesi kadar geç gider. Kafka kapalıysa olay outbox'ta bekler ve broker döndüğünde gönderilir. Kaybolmaz, yalnızca gecikir.
- **Çift e-posta:** en az bir kez teslimde tüketici aynı olayı iki kez görebilir. `processed_events` ile engellenir. Tek açık pencere şudur: e-posta gönderilir, `processed_events` yazılmadan süreç düşer. Bu durumda e-posta bir kez daha gider. Bu durum `design.md` D6'da kabul edilir.
- **Cache tutarsızlığı:** silme commit sonrası yapılır. Silme başarısız olursa (Redis o anda düşmüşse) eski görünüm en çok TTL (60 sn) kadar kalır. Stok ve fiyat kararı cache'ten okunmadığı için para hatası doğmaz.

# [ADR-112] Sipariş ödendi e-postaları olaydan, idempotent bir tüketiciyle gider; işlenemeyen olay `.dlt`'ye düşer

**Tarih:** 2026-11-26
**Durum:** Accepted · change `add-redis-cache-and-order-paid-event` D6 · [ADR-019](019-email-via-smtp-provider-agnostic.md)'un "gönderim hatası akışı kesmez" kuralı aynen geçerli

---

## Context

Sipariş ödendi e-postaları (alıcıya sipariş onayı, her satıcıya yeni sipariş) `CheckoutService.settle()` içinden `OrderNotifier.orderPaid` ile, transaction `afterCommit` aşamasında gidiyordu. Süreç commit ile gönderim arasında düşerse e-posta kaybolur ve yeniden denenmez.

[ADR-110](110-transactional-outbox-relay-skip-locked.md) ile aynı an, `ORDER_PAID` olayı olarak outbox'a yazılıyor ve Kafka'ya gidiyor. Outbox teslimi en az bir kezdir: aynı olay tüketiciye birden fazla ulaşabilir.

Kısıtlar:

- İki e-posta yolu aynı anda çalışamaz (§15: iki alternatifi birlikte uygulamak yasak; çift e-posta).
- Uygulama kodunda ham SQL yoktur (§11.4): `INSERT … ON CONFLICT DO NOTHING` kullanılamaz.
- Olay gövdesi kişisel veri taşımaz ([ADR-111](111-order-paid-event-contract-versioned-topic-no-pii.md)); e-posta adresi ve dil veritabanından okunur.

## Decision

1. **Tek yol:** `settle()` artık e-posta göndermez, yalnızca olayı yazar. `OrderPaidEmailConsumer`, `bahceden.order.paid.v1` topic'ini `bahceden-order-mail` grubuyla dinler. Eski çağrı ve `CheckoutService`'in `OrderNotifier` bağımlılığı aynı commit'te kalktı.
2. **İşleme tek transaction'dır:**
   1. `ProcessedEvents.markIfFirst("order-paid-email", eventId)`: `processed_events` (changeset `018`, birincil anahtar `(consumer, event_id)`) tablosunda kayıt varsa olay atlanır; yoksa kayıt eklenir. Varlık kontrolü + ekleme yapılır; eşzamanlı ikinci ekleme birincil anahtar çakışmasıyla geri alınır ve yeniden denemede olay işlenmiş görülür.
   2. Sipariş kimlikle okunur. Bulunamazsa `WARN` yazılır; olay işlenmiş sayılır ve yeniden denenmez.
   3. `OrderNotifier.orderPaid` çağrılır (mevcut kod, K9): metinler hesap dilinde hazırlanır, gönderim commit sonrasıdır.
3. **Offset** kayıt başarıyla işlendikten sonra yazılır (`ack-mode: record`, otomatik commit kapalı). Yeni grup topic'i baştan okur (`auto-offset-reset: earliest`).
4. **Hata işleyici** tek ve ortaktır (`KafkaConsumerErrorConfig`):
   - `DefaultErrorHandler`, 1 sn arayla toplam 3 deneme yapar, sonra kaydı aynı bölüm numarasıyla `<topic>.dlt` topic'ine yazar ve devam eder;
   - okunamayan gövde (`JsonProcessingException`) yeniden denenmez;
   - `bahceden.order.paid.v1.dlt` 3 bölümle açılışta tanımlanır.
5. **Saklama:** `processed_events` satırları outbox ile aynı süreden (7 gün) sonra silinir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| E-postayı `afterCommit` sırasında bırakıp olayı ek olarak yayınlamak | Olay tüketilmez; e-posta kaybı sorunu çözülmez |
| İki yolu bir süre birlikte çalıştırmak | Her siparişte çift e-posta; §15 yasağı |
| Tekrar korumasını Redis'te `SETNX` ile tutmak | TTL'ye bağlı; e-posta işlemiyle aynı transaction'da değil |
| `INSERT … ON CONFLICT DO NOTHING` (native) | Uygulama kodunda ham SQL yasak; varlık kontrolü + birincil anahtar aynı korumayı verir |
| Kafka exactly-once (transactional producer/consumer) | Yalnızca Kafka→Kafka zincirinde geçerli; e-posta yan etkisini kapsamaz |
| Sonsuz yeniden deneme (DLT yok) | Tek bozuk olay bölümü kilitler; arkadaki siparişlerin e-postası gitmez |

## Consequences

### Olumlu
- Commit sonrası süreç düşse de olay outbox'ta veya topic'te kalır; e-posta gönderilene kadar yeniden denenir.
- Tekrar teslim edilen olay ikinci e-posta göndermez (testle sabit).
- Bozuk olay arkadakileri bekletmez; `.dlt`'de incelenebilir.

### Olumsuz
- Sipariş onayı birkaç saniye gecikir: relay aralığı (2 sn) ve tüketici süresi kadar.
- Dar pencere: transaction commit olur (işlenmiş kaydı dahil), commit sonrası gönderim sürerken süreç düşerse e-posta gitmeyebilir. Bu pencere eski davranışla aynıdır; kötü sonuç çift e-posta değil eksik e-postadır.
- `.dlt` için otomatik yeniden oynatma yoktur; inceleme ve yeniden gönderim elle yapılır (izleme kararı B5).
- Testler e-postayı artık relay turu ve tüketici grubunun offset'ini bekleyerek doğrular.

### Nötr / Trade-off
- Tüketici aynı `api` sürecindedir; ayrı servise taşımak yalnızca dağıtım değişikliğidir, ama sipariş okuması için veritabanı erişimi gerekir.

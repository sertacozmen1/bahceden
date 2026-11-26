# [ADR-110] İş olayları transactional outbox ile yazılır; zamanlanmış relay `SKIP LOCKED` ile Kafka'ya aktarır

**Tarih:** 2026-11-26
**Durum:** Accepted · change `add-redis-cache-and-order-paid-event` D4 · [ADR-107](107-redis-and-kafka-added-to-budget.md) kullanım yeri

---

## Context

"Sipariş ödendi" anı `CheckoutService.settle()` içinde, stok kilidi ve sipariş durumu geçişiyle tek transaction'dadır ([ADR-066](066-payment-redirect-verify-lock-stock-on-success.md)). Bu anı Kafka'ya bir iş olayı olarak yayınlamak istiyoruz ([ADR-107](107-redis-and-kafka-added-to-budget.md)). Klasik tuzak **dual write**:

- Transaction içinde `send` edilirse mesaj gider, transaction geri alınabilir: olmayan bir ödeme yayınlanır.
- `afterCommit` sırasında `send` edilirse commit olur, süreç o anda düşer veya broker kapalıdır: olay kaybolur.

Ayrıca Kafka kesintisi ödemeyi durdurmamalıdır. `api` ileride birden fazla örnekle çalışabilir.

## Decision

1. **Outbox tablosu** (`outbox_events`, changeset `017`):

   | Kolon | Anlam |
   |---|---|
   | `event_id` | tüketicinin tekrar teslimi tanıdığı kimlik |
   | `event_type`, `topic`, `aggregate_key` | yönlendirme |
   | `payload` | JSONB, gönderilecek gövdenin kendisi |
   | `occurred_at` | olay anı |
   | `published_at` | relay gönderince dolar |
   | `attempts` | başarısız deneme sayısı |

   Kısmi indeks yalnızca yayınlanmamış satırları kapsar.
2. **Yazım çağıranın transaction'ındadır** (`OutboxPublisher.append`, `Propagation.MANDATORY`). Açık transaction yoksa hata verir. `settle()` başarılı ödemede `OrderPaidEvent` ekler; başarısız, süresi dolmuş veya stoksuz ödemede eklemez. Olay ya sipariş durumuyla birlikte kalıcı olur ya hiç olmaz.
3. **Relay** (`OutboxRelay.relayOnce`, varsayılan 2 sn aralıkla). Bir tur tek transaction'dır:
   - yayınlanmamış satırlar `FOR UPDATE SKIP LOCKED` ile, kimlik sırasıyla, en çok 100 adet alınır; JPQL + kilit zaman aşımı ipucu `-2` kullanılır, ham SQL yoktur;
   - her satır `KafkaTemplate.send(topic, key, payload)` ile gönderilir ve broker onayı beklenir (`acks=all`, idempotent üretici);
   - başarılı satır işaretlenir.
   - İlk hatada satırın `attempts` değeri artar ve tur biter: sıra korunur.
   - Bekleme süreleri kısadır (`max.block.ms` 5 sn, gönderim zaman aşımı 10 sn).
4. **Teslim en az bir kezdir.** Gönderim başarılı olduktan sonra commit'ten önce süreç düşerse olay bir kez daha gider. Tekrarı tüketici `event_id` ile ayıklar (tüketici kararı change'in Faz 4'ünde ADR-112 olarak yazılır).
5. **Saklama:** yayınlanmış satırlar 7 gün sonra silinir (`purgePublished`, temizlik aralığıyla). Yayınlanmamış satır silinmez.
6. **Zamanlama** diğer işlerle aynı desendedir: `@ConditionalOnProperty(bahceden.outbox.enabled)`. Testlerde kapalıdır, testler turu doğrudan çağırır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Transaction içinde `KafkaTemplate.send` | Geri alınan ödeme için olay gider |
| `afterCommit` sırasında `send` | Süreç düşerse veya broker kapalıysa olay kaybolur, yeniden denenmez |
| Kafka transaction'ı + `ChainedTransactionManager` | Deprecated; iki kaynakta gerçek atomiklik vermez, "en iyi çaba" sıralı commit'tir |
| Debezium / Kafka Connect ile CDC | Ayrı servis ve bağlayıcı işletimi; MVP ölçeği için ağır (§3) |
| Ham SQL `FOR UPDATE SKIP LOCKED` (`nativeQuery`) | AGENTS.md §11.4 uygulama kodunda ham SQL'i yasaklar; JPQL + kilit ipucu aynı SQL'i üretir |
| Hatalı satırı atlayıp devam etmek | Aynı anahtarın olay sırası bozulur; broker kapalıyken her satır boşuna denenir |

## Consequences

### Olumlu
- Olay sipariş durumuyla atomiktir: geri alınan ödeme olay bırakmaz, kalıcı ödeme olayını kaybetmez.
- Kafka kesintisi ödemeyi etkilemez; olay outbox'ta bekler, broker dönünce aynı sırayla gider.
- İki relay örneği aynı satırı göndermez (`SKIP LOCKED`, testle sabit).

### Olumsuz
- Olay en çok relay aralığı + gönderim süresi kadar gecikir.
- Relay turu Kafka onayını beklerken satır kilitlerini tutar; broker yavaşsa tur, gönderim zaman aşımı kadar uzar.
- En az bir kez teslim, tüketicide idempotency gerektirir.
- Yeni bir tablo ve zamanlanmış iş: izlenmesi gereken bir "yayınlanmamış satır sayısı" doğar (üretim izleme kararı B5'e bağlı).

### Nötr / Trade-off
- Bir broker hatasında tur durur; tek bir bozuk olay (ör. silinmiş topic) arkadakileri bekletir. MVP'de tek topic vardır; çok topic'e geçişte topic başına sıra yeniden değerlendirilir.
- Spring'in `KafkaException`'ı (gönderim anındaki eşzamanlı hata) ile Kafka istemcisinin `KafkaException`'ı ayrı tiplerdir; relay ikisini de yakalar. İlki broker'a hiç ulaşılamadığında ortaya çıktı ve testle bulundu.

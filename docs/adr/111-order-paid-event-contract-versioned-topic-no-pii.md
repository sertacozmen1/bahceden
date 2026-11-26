# [ADR-111] `ORDER_PAID` olay sözleşmesi: sürümlü topic, kişisel veri yok, anahtar sipariş numarası

**Tarih:** 2026-11-26
**Durum:** Accepted · change `add-redis-cache-and-order-paid-event` D5

---

## Context

[ADR-110](110-transactional-outbox-relay-skip-locked.md) ile olaylar Kafka'ya güvenilir biçimde gider. Olayın gövdesi `api` ile tüketiciler arasında yeni bir sözleşmedir. HTTP sözleşmesi `openapi.yaml`'dır ([ADR-009](009-contract-generated-from-spring.md)); olay sözleşmesi orada yer almaz.

Kısıtlar:

- Topic'e yazılan veri broker diskinde ve tüketici log'larında yaşar. Kişisel veri (ad, e-posta, adres, telefon) buraya girerse KVKK kapsamı genişler (K10).
- Schema registry ve Avro bütçe dışıdır ([ADR-107](107-redis-and-kafka-added-to-budget.md)).
- Para minor unit + para birimidir ([ADR-006](006-money-as-minor-unit.md)), zaman UTC ISO-8601'dir.

## Decision

1. **Topic:** `bahceden.order.paid.v1`, 3 bölüm; kopya sayısı broker varsayılanıdır. Uygulama açılışta tanımlar (`NewTopic`); broker'da otomatik topic açılışı kapalıdır.
2. **Anahtar:** sipariş numarası (`BHC-…`). Aynı siparişin olayları aynı bölümde sıralı kalır.
3. **Gövde** (`OrderPaidEvent` record, JSON):

   ```json
   {
     "eventId": "0b7c2f4e-…",
     "eventType": "ORDER_PAID",
     "occurredAt": "2026-11-26T10:15:30Z",
     "orderId": 42,
     "orderNumber": "BHC-FH5M426922",
     "sellerOrderIds": [101, 102],
     "grandTotal": { "amount": 25550, "currency": "TRY" }
   }
   ```

   Kişisel veri yoktur. Tüketici e-posta adresi veya hesap dili gibi bir şeye ihtiyaç duyarsa kimlikle veritabanından, yetkili olarak okur. Alan kümesi testle sabittir (fazla alan testi kırar).
4. **Sürümleme:** alan eklemek kırıcı değildir. Alan silmek, yeniden adlandırmak veya anlamını değiştirmek yeni topic (`.v2`) ile yapılır. Eski topic, tüketiciler geçene kadar beslenir.
5. **Katalog:** olaylar [`docs/reference/04-event-catalog.md`](../reference/04-event-catalog.md) sayfasında tutulur.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Alıcının e-postasını ve dilini gövdeye koymak | Kişisel veri broker'a ve log'lara girer; tüketici DB'ye gitmeden e-posta atabilirdi ama KVKK yüzeyi büyür |
| Siparişin tamamını (satırlar, adres) gövdeye koymak | Kişisel veri ve geniş sözleşme; her alan değişikliği olayı kırar |
| Avro + schema registry | Yeni servis ve paket; tek olay için ağır |
| Sürümü gövdede taşımak (`"version": 1`) | Tüketici her mesajı açıp dallanmak zorunda kalır; topic adı ile sürüm, eski ve yeni tüketiciyi ayırır |
| Anahtar olarak sipariş kimliği (`orderId`) | İç kimlik; sipariş numarası kullanıcıya görünen iş anahtarıdır ve log'larda zaten kullanılır |

## Consequences

### Olumlu
- Topic ve log'lar kişisel veri taşımaz.
- Gövde küçük ve kararlıdır; tüketiciler eklenen alanlardan etkilenmez.

### Olumsuz
- Tüketici çoğu iş için veritabanına gitmek zorundadır; tüketiciler aynı süreçte olduğu sürece sorun değildir, ayrı serviste ise okuma yetkisi gerekir.
- Şema derleme zamanında doğrulanmaz; sözleşme record ve test ile korunur.

### Nötr / Trade-off
- Olay sözleşmesi `openapi.yaml`'da değildir; HTTP sözleşmesinin tazelik testi bu olayı kapsamaz.

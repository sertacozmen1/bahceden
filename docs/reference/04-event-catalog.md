# Olay Kataloğu

**Son doğrulama:** 2026-11-26 · Kararlar: [ADR-110](../adr/110-transactional-outbox-relay-skip-locked.md) (outbox ve relay), [ADR-111](../adr/111-order-paid-event-contract-versioned-topic-no-pii.md) (olay sözleşmesi)

> Bu sayfa `api`'nin Kafka'ya yayınladığı iş olaylarının **mevcut** dökümüdür. HTTP sözleşmesi `shared/contracts/openapi.yaml`'dır; olay sözleşmesi orada yer almaz, burada tutulur. Kaynak kod esastır: olay tipleri `OutboxMessage` arayüzünü uygulayan record'lardır. Olay eklendiğinde veya değiştiğinde bu sayfa aynı commit'te güncellenir.

---

## Ortak kurallar

| Kural | Değer |
|---|---|
| Yayın yolu | Transactional outbox: olay iş verisiyle aynı transaction'da `outbox_events`'e yazılır, relay sırayla gönderir |
| Teslim | **En az bir kez.** Tüketici `eventId` ile tekrarı ayıklar (`processed_events`, [ADR-112](../adr/112-order-paid-emails-from-idempotent-consumer.md)) |
| Hata | Ortak hata işleyici: 1 sn arayla 3 deneme, sonra `<topic>.dlt`; okunamayan gövde yeniden denenmez |
| Anahtar | Olayın iş anahtarı; aynı anahtarın olayları aynı bölümde sıralı |
| Gövde | JSON, `camelCase`; tip bilgisi yok |
| Kişisel veri | **Yok.** Ad, e-posta, adres, telefon, IBAN, takip numarası olaya girmez (K10) |
| Para | `{"amount": <kuruş>, "currency": "TRY"}` ([ADR-006](../adr/006-money-as-minor-unit.md)) |
| Zaman | UTC, ISO-8601 |
| Sürüm | Topic adında (`.v1`). Alan eklemek kırıcı değil; silmek/yeniden adlandırmak yeni topic |
| Topic oluşturma | Uygulama açılışında (`NewTopic`); broker'da otomatik topic açılışı kapalı |

## `bahceden.order.paid.v1`

Sipariş ödendi: sağlayıcı ödemeyi doğruladı, stok yetti ve sipariş `PAID` oldu. Başarısız, süresi dolmuş veya stok yetmediği için iade edilen ödeme bu olayı **üretmez**.

| | |
|---|---|
| Üretici | `CheckoutService.settle()` (ödeme dönüşü ve ödenmemiş sipariş işi) |
| Anahtar | `orderNumber` |
| Bölüm | 3 |
| Tüketiciler | `OrderPaidEmailConsumer` (grup `bahceden-order-mail`): alıcıya sipariş onayı, her satıcıya yeni sipariş e-postası, hesap dilinde ([ADR-112](../adr/112-order-paid-emails-from-idempotent-consumer.md)) |
| Ölü mektup | `bahceden.order.paid.v1.dlt` (3 bölüm): üç denemede işlenemeyen veya okunamayan kayıt, aynı bölüm numarasıyla |

```json
{
  "eventId": "0b7c2f4e-5a1d-4c3b-9f6e-2d8a7b1c4e90",
  "eventType": "ORDER_PAID",
  "occurredAt": "2026-11-26T10:15:30Z",
  "orderId": 42,
  "orderNumber": "BHC-FH5M426922",
  "sellerOrderIds": [101, 102],
  "grandTotal": { "amount": 25550, "currency": "TRY" }
}
```

| Alan | Tip | Not |
|---|---|---|
| `eventId` | `uuid` | Tekrar teslim kimliği; `outbox_events.event_id` |
| `eventType` | `string` | Her zaman `ORDER_PAID` |
| `occurredAt` | `date-time` | Ödemenin kaydedildiği an |
| `orderId` | `integer` | Sipariş kimliği |
| `orderNumber` | `string` | Kullanıcıya görünen sipariş numarası |
| `sellerOrderIds` | `integer[]` | Siparişin satıcı siparişleri |
| `grandTotal` | `Money` | Alıcının ödediği toplam (ürünler + kargo) |

Alan kümesi `OutboxTest` ile sabittir. Alan **sırası** garanti değildir: gövde PostgreSQL `jsonb` kolonundan okunur ve normalleştirilir; tüketici alanları adıyla okur.

## Yerelde izleme

`docker compose up` ile Kafka makineden `localhost:9094` adresinden erişilebilir. Topic'i baştan okumak için:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic bahceden.order.paid.v1 \
  --from-beginning --property print.key=true
```

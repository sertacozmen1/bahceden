# [ADR-092] E-postalarda tarih-saat Türkiye saatiyle, hesabın dilinde ve sabit kalıpla; tek yardımcıda

**Tarih:** 2026-10-19
**Durum:** Accepted
**Kaynak:** `add-delivered-email` design.md D2

---

## Context

Mevcut e-postalar yalnızca göreli süre veriyordu ("48 saat içinde", "3 iş günü içinde"). Bu e-postalar bir eylemin hemen ardından gidiyor. "Teslim edildi" e-postası ([ADR-091](091-delivered-email-to-buyer-with-claim-deadline.md)) ise sorgu aralığı kadar gecikebilir ve saatler sonra okunabilir; göreli süre tek başına yanıltır.

`api` içinde tarih-saat biçimleyen kod yoktu. Para biçimi tek yardımcıda (`MoneyFormat`), Türkiye saat dilimi tek sabitte (`BusinessDays.TURKEY`). Pazaryeri tek bölgedir ([ADR-063](063-delivery-provinces-config-address-book.md)); hesapta saat dilimi tutulmuyor.

## Decision

- Son an e-postada iki biçimde geçer: tarih-saat ve saat olarak süre. Metin saat dilimini açıkça yazar ("Türkiye saatiyle", "Turkey time").
- Biçim tek yardımcıdadır: `common/time/DateTimeFormat.format(Instant, SupportedLocale)`.
  - Saat dilimi `Europe/Istanbul`.
  - Kalıp sabittir: `d MMMM yyyy HH:mm`. Ay adı hesabın dilinde tam yazılır: "19 Ekim 2026 14:30", "19 October 2026 14:30".
- Yeni bir e-posta tarih-saat gösterecekse bu yardımcıyı kullanır.

| Alternatif | Neden reddedildi |
|---|---|
| Yalnızca göreli süre | Gecikmeli okunan e-postada yanlış anlaşılır. |
| UTC ile göstermek | Alıcıya bir dönüşüm daha yükler. |
| `messages.properties`'te `{0,date,…}` kalıbı | `MessageFormat` JVM varsayılan saat dilimiyle çalışır; saat dilimi denetimi kaybolur. |
| `java.time` yerel biçim stilleri (`FormatStyle`) | Çıktı JDK'nın CLDR sürümüyle değişir (kısaltılmış ay, İngilizcede 12 saat ve özel boşluk karakteri); metin ve test öngörülemez olur. |
| Biçimi `OrderNotifier` içinde özel metotta yapmak | İkinci kullanımda kopyalanır. |

## Consequences

### Olumlu
- E-postadaki an, sipariş detayındaki geri sayımla aynı bilgiyi verir ve okunduğu andan bağımsızdır.
- Çıktı Java sürümünden bağımsız ve testle sabittir.

### Olumsuz
- İngilizce okuyan alıcı da 24 saat biçimi görür.
- Türkiye dışında yaşayan alıcı kendi saatine çevirmek zorundadır; metin saat dilimini söyler.

### Nötr / Trade-off
- Ekrandaki tarih biçimi `web`'in işi olarak kalır (`Intl`); bu yardımcı yalnızca sunucunun ürettiği metinler içindir.

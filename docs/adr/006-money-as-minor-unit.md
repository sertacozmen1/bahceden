# [ADR-006] Para minor unit tamsayı olarak taşınır; hesap yalnızca sunucuda yapılır

**Tarih:** 2026-10-06
**Durum:** Accepted

---

## Context

Pazaryerinde para birden fazla yerde görünüyor: birim fiyat, kalem toplamı, kargo ücreti, komisyon, satıcı hakediş tutarı, iade. Bu değerler hem veritabanında saklanıyor, hem sözleşmede taşınıyor, hem ekranda gösteriliyor.

İki klasik hata var:

1. **Kayan noktalı sayı ile para tutmak.** `0.1 + 0.2 != 0.3`; kuruş kaybı sipariş sayısıyla birlikte büyüyor ve mutabakat tutmuyor.
2. **Toplamı istemcide hesaplamak.** İstemci kendi hesabını gönderirse manipüle edilebilir; iki istemci iki farklı yuvarlama yaparsa sipariş ile tahsilat ayrışır.

## Decision

- Para **minor unit tamsayı** olarak temsil edilir: Java'da `long` (kuruş), veritabanında `BIGINT`, sözleşmede `integer`. Yanında her zaman açık bir `currency` (`TRY`) taşınır.
- Sözleşmedeki ve API yanıtlarındaki biçim tek: `{"amount": 12550, "currency": "TRY"}` (125,50 TL).
- `double` ve `float` ile para hesabı **yasaktır**. `BigDecimal` de birincil temsil olarak kullanılmaz; yalnızca oran/komisyon hesabının ara adımında kullanılabilir ve sonuç tamsayıya yuvarlanır.
- **Yuvarlama tek bir yardımcıda** yapılır ve `critical` testle sabitlenir ([ADR-005](005-test-strategy.md)). İki yerde yuvarlama kuralı yazılamaz.
- **Tüm para hesapları sunucuda** yapılır: birim fiyat, kalem toplamı, kargo ücreti, komisyon ve genel toplam `api/` tarafından hesaplanır. `web/` gelen sayıyı **gösterir**, hesaplamaz; istemciden gelen tutar asla güvenilmez (AGENTS.md §11.1, §12.1).
- Gösterim biçimlendirmesi `web/src/lib/money.ts` içinde tek bir yardımcıda, `Intl.NumberFormat` ile yapılır; bileşen içinde elle biçimlendirme yapılmaz.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `BigDecimal` (ölçekli ondalık) | Doğru sonuç veriyor ama JSON'a dizilirken (`12.50` / `12.5` / `"12.50"`) biçim belirsizliği doğuyor, istemci tarafında `number` ile kayan noktaya düşüyor ve eşitlik karşılaştırmaları `scale` yüzünden tuzaklı. Tamsayı kuruş daha az sürprizli. |
| `double` / `float` | Kayan nokta hatası; para için kabul edilemez. |
| Ondalık metin (`"125.50"` string) | Her kullanımda ayrıştırma gerekiyor ve matematik yine başka bir tipte yapılıyor; doğrulaması zor. |
| Para birimi alanını atlamak (her şey TRY) | Tek para birimi bugün doğru ama alanın yokluğu ileride veri göçü demek; alan maliyeti sıfıra yakın. |

## Consequences

### Olumlu
- Kuruş kaybı ve yuvarlama sapması yapısal olarak imkânsız; mutabakat tutuyor.
- JSON'da tek ve net biçim: istemci tarafı ayrıştırma belirsizliği yok.
- Toplamlar tek yerde hesaplandığı için manipülasyona kapalı ve testle kilitli.

### Olumsuz
- Her okuma ve yazmada 100'e bölme/çarpma gerekiyor; yardımcı fonksiyonlar olmadan okunurluk düşüyor (bu yüzden tek yardımcı zorunlu).
- Veritabanında değeri çıplak gözle okumak daha zor (`12550`).

### Nötr / Trade-off
- Oran içeren hesaplar (komisyon yüzdesi, KDV) ara adımda ondalık kullanıyor; kuralın tamamı tek yardımcıda ve testte toplanıyor.

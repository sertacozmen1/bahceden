# [ADR-011] Liste sorguları ilişkiyi fetch join ile getirir ve sorgu sayısı testle sabitlenir

**Tarih:** 2026-10-06
**Durum:** Accepted
**Kaynak:** `add-storefront-product-listing` change, `design.md` D2

---

## Context

Vitrin listesi her ürünü üreticisiyle birlikte gösteriyor. JPA'da `@ManyToOne` varsayılan olarak tembel yüklenir; liste döngüsünde her ürün için ayrı bir sorgu çalışır (N+1). Sayfa başına 20 üründe bu 20 fazladan sorgu demek ve vitrin en çok çağrılan uç.

`AGENTS.md` §11.4 N+1'i yasaklıyor. Ancak yasak, onu ölçen bir mekanizma olmadan sözde kalır: `fetch` ayarı sessizce değişebilir, yeni bir alan erişimi yeni bir tembel yükleme tetikleyebilir ve kod incelemesi bunu her seferinde yakalamaz.

## Decision

1. Liste sorguları ihtiyaç duydukları ilişkileri **açık `join fetch`** ile tek sorguda getirir. Sayfalı sorgularda sayım sorgusu ayrıca ve açıkça yazılır (`countQuery`): türetilmiş sayım, fetch join'i taşıyamaz.
2. **Sorgu sayısı testle sabitlenir.** Entegrasyon testi Hibernate istatistiklerini sıfırlar, listeyi çeker, ilişkili alana erişir ve çalışan ifade sayısının **kayıt sayısından bağımsız** olduğunu doğrular (içerik + sayım = 2).

Bu iddia her liste ucu için tekrarlanır; yeni bir liste ucu eklenirken sorgu sayısı testi de yazılır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Yalnızca kod incelemesine güvenmek | Fetch ayarları ve alan erişimleri sessizce değişir; regresyon üretimde fark edilir. |
| İlişkiyi `FetchType.EAGER` yapmak | Tüm sorgulara yayılır: ilişkiye ihtiyaç duymayan yollar da bedel öder ve kontrol entity'de gizlenir. |
| `@BatchSize` / `fetch` modu ayarları | N+1'i azaltır ama ortadan kaldırmaz; sorgu sayısı yine veri hacmine bağlı kalır. |
| Sorgu süresi (ms) üzerinden eşik testi | Makineye ve yüke duyarlı, kırılgan test. Sorgu **sayısı** deterministik ve anlamlı. |

## Consequences

### Olumlu
- Vitrin sorgusu öngörülebilir: ürün sayısı artarken sorgu sayısı sabit.
- Regresyon commit anında yakalanıyor; performans sorunu üretime taşınmıyor.
- Test, niyeti belgeliyor: "bu liste tek sorguda gelir" iddiası okunabilir halde.

### Olumsuz
- Her liste ucu için fazladan bir test yazma yükü.
- Testin Hibernate istatistiklerine bağımlı olması bir uygulama detayına yaslanmak demek; `generate_statistics` test profilinde açık tutulmak zorunda.
- `countQuery` elle yazıldığı için içerik sorgusuyla süzgeç koşulları arasında tutarlılık disiplini gerekiyor (ikisi ayrışırsa toplam sayı yanlış olur).

### Nötr / Trade-off
- Fetch join, sayfalamayla birlikte yalnızca tekil (`@ManyToOne`) ilişkilerde güvenlidir; koleksiyon ilişkisinde sayfalama bellekte yapılır. Koleksiyon içeren bir liste ucu gerektiğinde ayrı bir çözüm (iki aşamalı sorgu) gerekecek.

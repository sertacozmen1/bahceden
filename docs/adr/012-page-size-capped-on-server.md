# [ADR-012] Sayfa boyutunun üst sınırı sunucuda uygulanır; aşan istek reddedilmez, sınıra çekilir

**Tarih:** 2026-10-06
**Durum:** Accepted
**Kaynak:** `add-storefront-product-listing` change, `design.md` D3

---

## Context

Liste uçları `page` ve `size` parametreleri alıyor. `size` sınırsız bırakılırsa tek bir istek (`size=100000`) veritabanını, uygulama belleğini ve ağı tüketebilir; bu, kimlik doğrulaması gerekmeyen bir vitrin ucunda doğrudan hizmet kesintisi riskidir.

İkinci soru: sınır aşıldığında ne yapmalı? Hata dönmek mi, sınırlamak mı?

## Decision

- Varsayılan sayfa boyutu **20**, üst sınır **50**. Sınır sunucu kodunda sabittir; istemci onu değiştiremez.
- Üst sınırı aşan bir `size` değeri **reddedilmez**: sunucu değeri sınıra çeker ve yanıtta **uygulanan** boyutu bildirir (`size` alanı). İstemci ne aldığını yanıttan öğrenir.
- Negatif veya sayı olmayan değer ise doğrulama hatasıdır (400, ortak hata gövdesi): bu bir istemci hatasıdır, sessizce düzeltilmez.
- Sınırlama kuralı HTTP katmanında değil service katmanında uygulanır: kural, çağıran kim olursa olsun geçerlidir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Sınır aşımında 400 dönmek | Vitrinde kullanıcıya yansıyan gereksiz bir hata üretir; istemcinin büyük bir sayfa istemesi kötü niyet değil, çoğu zaman bilgisizliktir. Kaynak koruması, kullanıcıya hata göstermeyi gerektirmiyor. |
| Sınırı yapılandırmaya taşımak | Bugün tek bir doğru değer var; yapılandırma, üzerine düşünülmesi gereken bir ayar daha demek (`AGENTS.md` §3 over-engineering). |
| Sınır koymamak, yalnızca izlemek | Riski üretime erteler; kimlik gerektirmeyen uçta kabul edilemez. |
| Cursor tabanlı sayfalama | Derin sayfalama ve kararlı sıralama için daha iyi ama MVP'de sabit sıralı kısa liste için gereksiz karmaşıklık. Sonsuz kaydırma ihtiyacı doğarsa ayrı karar. |

## Consequences

### Olumlu
- Tek bir istekle kaynak tüketme yolu kapalı.
- İstemci her zaman ne aldığını biliyor: yanıt uygulanan boyutu bildiriyor.
- Kural service katmanında, dolayısıyla test edilebilir ve HTTP'den bağımsız.

### Olumsuz
- İstemci istediğinden az kayıt alabilir ve bunu ancak yanıta bakarak anlar; bu davranışın sözleşmede açıkça yazılması şart (yazıldı).
- Sabit sınır, ileride toplu veri çeken yönetim ekranları için yetersiz kalabilir; o ihtiyaç ayrı bir uç veya ayrı bir karar gerektirir.

### Nötr / Trade-off
- Derin sayfalama (`page=5000`) hâlâ maliyetli bir `OFFSET` üretir. Bugünkü veri hacminde sorun değil; sorun olduğunda cursor sayfalaması yeniden değerlendirilir.

# [ADR-007] Satıcı veri izolasyonu sorgu seviyesinde yapılır; varsayılan erişim reddedilir

**Tarih:** 2026-10-06
**Durum:** Accepted

---

## Context

Bahçeden çok satıcılı bir pazaryeri: aynı veritabanında yüzlerce satıcının ürünü, siparişi, gönderimi ve alıcısının kişisel verisi yan yana duruyor. Bir satıcının başka bir satıcının siparişini görmesi yalnızca bir hata değil; KVKK kapsamında veri ihlali ve ticari sır sızıntısı.

Yaygın ve kırılgan desen şudur:

```java
var order = orderRepository.findById(id).orElseThrow();
if (!order.getSellerId().equals(currentSellerId)) {   // unutulması çok kolay
    throw new ForbiddenException();
}
```

Bu kontrol bir endpoint'te unutulduğunda sessizce açık kalıyor; kod incelemesi her seferinde yakalayamıyor. Ayrıca hata 403 dönerse kaydın **varlığını** sızdırıyor ("bu sipariş numarası var ama senin değil").

## Decision

- **Varsayılan deny.** Her endpoint açıkça yetkilendirilir; herkese açık uçlar (vitrin, ürün detayı, kayıt, giriş) açıkça işaretlenir. Roller: `ROLE_CUSTOMER`, `ROLE_SELLER`, `ROLE_ADMIN`.
- **Sahiplik kontrolü sorgu seviyesindedir.** Satıcıya ait veri `findById` ile çekilip sonradan karşılaştırılmaz; sorgunun kendisi kapsamlıdır:

```java
var order = orderRepository.findByIdAndSellerId(id, currentSellerId)
        .orElseThrow(OrderNotFoundException::new);
```

- Liste uçları da aynı kuralı izler: `findAllBySellerId(...)`. Kapsamsız bir "tüm kayıtlar" sorgusu (`findAll`) satıcı verisi üzerinde **yasaktır**; yalnızca `ROLE_ADMIN` uçlarında ve açık gerekçeyle kullanılabilir.
- Sahiplik ihlali **404** döner, 403 değil: kaydın varlığı sızdırılmaz (AGENTS.md §11.5).
- Her sahiplik kuralının **`critical` testi** vardır: başkasının kaydına erişim denemesi 404 döndüğü testle sabitlenir ([ADR-005](005-test-strategy.md)).
- Kimlik `web/`'den gelen bir parametreden değil, sunucudaki kimlik doğrulama bağlamından okunur. İstemciden gelen `sellerId` asla yetkilendirme kaynağı değildir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Servis katmanında `if` ile sahiplik kontrolü | Unutulması kolay ve denetlenmesi zor; bir endpoint'in atlaması tüm izolasyonu bozuyor. Sorgu seviyesinde kontrol "unutulursa sonuç boş gelir" güvenli başarısızlığını veriyor. |
| Metot seviyesi `@PreAuthorize` ifadeleri | Rol kontrolü için uygun ama satır seviyesi sahipliği ifade etmek için veriyi önce yüklemek gerekiyor; ifade dili karmaşıklaşıyor ve testi zorlaşıyor. Rol kontrolü için kullanılır, satır sahipliği için değil. |
| PostgreSQL Row Level Security (RLS) | Güçlü ama bağlantı başına oturum değişkeni yönetimi, connection pool ile etkileşimi ve hata ayıklama maliyeti MVP için ağır. İhtiyaç büyürse ADR ile yeniden değerlendirilir. |
| Satıcı başına ayrı şema veya ayrı veritabanı | Tek sunuculu MVP için fazlasıyla karmaşık: migration çoğalması, bağlantı yönetimi, raporlama zorluğu (AGENTS.md §3). |
| Sahiplik ihlalinde 403 dönmek | Kaydın varlığını doğruluyor; numara deneyerek rakip satıcının sipariş hacmi çıkarılabiliyor. |

## Consequences

### Olumlu
- İzolasyon yapısal: kontrolü "unutmak" sonucu açık değil **boş** yapıyor.
- Kaydın varlığı sızmıyor; numara tarama saldırısı bilgi vermiyor.
- Kural test edilebilir ve her endpoint için aynı: review'da kontrol listesi net.

### Olumsuz
- Repository arayüzleri daha kalabalık (`findByIdAndSellerId`, `findAllBySellerId`); jenerik bir temel repository ile kısaltılamıyor.
- Admin ekranları için ayrı, açıkça işaretlenmiş kapsamsız sorgular gerekiyor.

### Nötr / Trade-off
- 404 dönmek, gerçekten var olmayan kayıt ile yetkisiz kayıt arasındaki ayrımı loglara bırakıyor: hata ayıklama sırasında sunucu log'una (maskeli) sebep yazılır, istemciye yazılmaz.

# [ADR-109] Vitrin okumaları Redis'te kısa süre cache'lenir; değişiklikte commit sonrası silinir

**Tarih:** 2026-11-26
**Durum:** Accepted · change `add-redis-cache-and-order-paid-event` D3 · [ADR-107](107-redis-and-kafka-added-to-budget.md) kullanım yeri

---

## Context

Ürün detayı (`GET /api/v1/products/{slug}`) ve vitrin listesi (`GET /api/v1/products`) kimlik istemez, en çok okunan uçlardır ve nadiren değişir. Her istek birkaç sorgu çalıştırır (ürün + satıcı birleşimi, kapak veya bütün fotoğraflar).

Kısıtlar:

- Fiyat ve stok kararı ödeme anında kilitli okumayla verilir ([ADR-066](066-payment-redirect-verify-lock-stock-on-success.md)); vitrin yalnızca gösterir.
- `purchasable` alanı dört şeye bağlıdır: ilan durumu, satıcı onayı, satıcının etkin ödeme hesabı ve stok (`openspec/specs/storefront-catalog` "Satın alınabilirlik"). Bunlar dört ayrı servisten değişir (`catalog`, `seller`, `payment`, `order`).
- Vitrin yanıtları dilden bağımsızdır: metin üretilmez, birim bir enum'dur.
- Görünmeyen ürün (taslak, kaldırılmış, satıcısı onaysız) "yok" gibi 404 döner; cache bu kuralı delmemeli.

## Decision

1. **Spring Cache + Redis.** İki cache:
   - `storefront-product`: anahtar `slug`, değer `ProductDetail`;
   - `storefront-list`: anahtar `sayfa:boyut` (boyut üst sınıra çekilmiş), değer `PageResponse<ProductSummary>`; yalnızca ilk 3 sayfa cache'lenir.
2. **Kısa TTL:** 60 sn (`bahceden.cache.storefront-ttl`). TTL, silme kaçırılırsa eski görünümün üst sınırıdır.
3. **Silme commit sonrasıdır** (`StorefrontCache`, `TransactionSynchronization.afterCommit`):
   - ürüne özgü değişiklikte o ürünün detayı ve liste cache'inin tamamı silinir: ilan güncelleme, fotoğraf ekleme/silme/sıralama, satıcı ve admin durum geçişleri (tek ortak yol `ListingService.apply`), ödemede stok düşmesi, iptalde stok iadesi;
   - satıcı düzeyindeki değişiklikte iki cache tümden temizlenir: satıcı kararı (`SellerService.decide`), ödeme hesabı kaydı (`PayoutAccountService.save`).
4. **404 cache'lenmez:** detay istisnayla döner, Spring Cache istisnada yazmaz. Boş değer de yazılmaz (`disableCachingNullValues`).
5. **Değer biçimi:** cache başına sabit tipli JSON (`Jackson2JsonRedisSerializer`, uygulamanın `ObjectMapper` kopyası). Redis'te sınıf adı tutulmaz.
6. **Fail-open:** `CacheErrorHandler` okuma/yazma/silme hatasını `WARN` ile loglar ve veritabanından devam eder. Commit sonrası silme hatası da yalnızca loglanır.
7. **Sınır:** cache adları yalnızca bu ikisidir (`disableCreateOnMissingCache`). Hesap, sepet, sipariş, satıcı paneli ve admin yanıtları cache'lenmez.
8. **Yerleşim:** cache adları ve silme `common/cache/StorefrontCache` sınıfındadır, çünkü `seller` ve `payment` de çağırır ve `catalog`'a bağımlı olmamalıdır. Değer tiplerini bilen yapılandırma `catalog/StorefrontCacheConfig` sınıfındadır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Yalnızca TTL'ye güvenmek (silme yok) | Satıcı fiyatı değiştirince veya admin ürünü kaldırınca dakikalarca eski görünüm kalır |
| Write-through (yazmada DTO'yu yeniden üretip yazmak) | Yazma yolu fotoğraf ve satıcı birleşimini yeniden kurar; dört servise DTO üretimi sızar |
| Satıcı düzeyinde satıcının ürünlerini tek tek silmek | Ürün adreslerini toplamak için ek sorgu ister; olay seyrek, tüm cache'i temizlemek basit ve doğru |
| Commit öncesi silme (`@CacheEvict`) | Geri alınan transaction cache'i boşa siler; daha kötüsü, commit öncesi eşzamanlı okuma eski değeri silmeden sonra yeniden yazar |
| `GenericJackson2JsonRedisSerializer` (tip bilgili) | Sınıf adını Redis'e yazar; JDK değiştirilemez listeleri tip bilgisiyle geri okunamaz |
| HTTP cache (`Cache-Control`) / Next.js ISR | Değerli ama ayrı karar; satıcı değişikliğinin anında görünmesi için ayrıca geçersiz kılma ister |
| Caffeine (süreç içi) | Çok örnekte her örneğin cache'i ayrı silinmez; tutarsız |

## Consequences

### Olumlu
- Tekrarlanan detay okuması veritabanına gitmez (sorgu sayısı testiyle sabit).
- Değişiklik commit olduktan sonraki ilk okumada görünür; görünmeyen ürün cache'ten dönmez.
- Redis kesintisi vitrini kırmaz.

### Olumsuz
- Vitrini etkileyen her yeni yazma yolu silme çağrısını hatırlamak zorundadır; unutulursa eski görünüm TTL kadar kalır. Bugünkü yollar `StorefrontCacheTest` ile sabitlendi.
- Commit ile silme arasında başlayan bir okuma, eski değeri silmeden sonra yazabilir; bu dar pencere TTL ile sınırlıdır.
- Testler veriyi JDBC ile kurduğu için cache'i görmez; her test metodundan önce cache'i boşaltan bir dinleyici (`CacheResetListener`) gerekti.
- Redis kesintisinde her vitrin isteği bağlantı zaman aşımı kadar gecikebilir.

### Nötr / Trade-off
- Ürüne özgü değişiklik bütün liste sayfalarını siler; sayfa sayısı 3 ile sınırlı olduğu için maliyet düşüktür.
- `ProductService.MAX_PAGE_SIZE` ve `CACHED_PAGES` cache anahtarında SpEL ile okunabilmek için `public` oldu.

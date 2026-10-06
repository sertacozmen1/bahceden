# Design: add-storefront-product-listing

> Her `D<n>` maddesi archive'dan önce `docs/adr/` altına ADR olur (`AGENTS.md` K1 / §5.2). Kabul edilmiş bir ADR'ye aykırı karar yazılamaz.
> Mevcut ADR'lerden türeyen ve yeni karar **içermeyen** uygulama detayları burada D maddesi değildir; yalnızca ilgili ADR'ye atıf yapılır.

## Kararlar

### D1 · Ürün yayın durumu bir enum'dur, boolean değil

`products.status` alanı `DRAFT | PUBLISHED | ARCHIVED` enum'u olur; `is_active` boolean olmaz.

**Gerekçe:** Satıcı ilanı hazırlarken kaydedip sonra yayınlayacak (A3 kararıyla ön onay da eklenebilir: `PENDING_REVIEW`). Boolean, üçüncü durum geldiğinde şema göçü ve her sorguda ikinci bir koşul demek. Vitrin sorgusu `status = 'PUBLISHED'` ile başlar ve bu koşul ileride değişmez.

**Reddedilen alternatif:** `is_active boolean` + sonradan ikinci bayrak eklemek. Reddedildi: iki bayrağın tutarsız kombinasyonları (aktif + arşiv) mümkün hale geliyor ve geçersiz durum veritabanında temsil edilebilir oluyor.

### D2 · Listeleme sorgusu üreticiyi tek sorguda getirir ve sorgu sayısı testle sabitlenir

`GET /api/v1/products` sorgusu `Seller` ilişkisini `join fetch` ile çeker; listeleme için ürün sayısından bağımsız **sabit** sorgu sayısı (sayfa içeriği + toplam sayım) hedeflenir ve bu iddia testle sabitlenir.

**Gerekçe:** `AGENTS.md` §11.4 N+1'i yasaklıyor ama "yasak" bir test olmadan sözde kalır. Vitrin en çok çağrılan uç; sayfa başına 20 üründe N+1 20 ek sorgu demek. İddianın testle sabitlenmesi, ileride biri `@ManyToOne(fetch = LAZY)` ayarını değiştirdiğinde regresyonu yakalar.

**Reddedilen alternatif:** Yalnızca kod incelemesine güvenmek. Reddedildi: ilişki ayarları ve varsayılan fetch davranışı sessizce değişebiliyor.

### D3 · Sayfalama `page`/`size` ile yapılır, üst sınır sunucuda sabittir

Parametreler `page` (0'dan başlar) ve `size` (varsayılan 20, **üst sınır 50**). Sınır aşılırsa istek reddedilmez, `size` sunucuda sınıra çekilir ve yanıt gerçek `size` değerini döner.

**Gerekçe:** `AGENTS.md` §11.4 üst sınırın sunucuda sabit olmasını istiyor: aksi halde `size=100000` ile veritabanı tüketilebiliyor. Hata yerine sınırlama tercih edildi çünkü bu bir istemci hatası değil, kaynak koruması; istemciye 400 dönmek vitrinde kullanıcıya hata göstermek demek.

**Reddedilen alternatifler:** (a) Sınır aşımında 400 dönmek — reddedildi: vitrinde kullanıcıya yansıyan gereksiz hata. (b) Cursor tabanlı sayfalama — reddedildi: MVP'de sabit sıralı liste için gereksiz karmaşıklık; sonsuz kaydırma ihtiyacı doğarsa ayrı karar.

### D4 · `sellers` tablosu A3 kararından önce, bilinçli olarak minimum doğar

`sellers` tablosu bu dilimde yalnızca `id`, `slug`, `display_name`, `city`, `created_at`, `updated_at` taşır. Hesap ilişkisi (`account_id`), vergi/belge alanları ve onay durumu **yoktur**; bunlar KALANLAR A3 kararıyla `V2` migration'ı olarak gelir.

**Gerekçe:** Vitrinin değer önerisi üreticiyi göstermekten geçiyor (`docs/product/index.md`); üretici adı olmadan liste ürünün özünü anlatmıyor. A3 kararını beklemek ilk dilimi süresiz bloklardı. Alternatif olarak ürün üzerinde serbest metin `producer_name` alanı tutmak, A3 geldiğinde veri göçü ve ikinci bir gerçek kaynağı yaratırdı.

**Reddedilen alternatifler:** (a) Ürün üzerinde serbest metin üretici adı — reddedildi: normalleştirme kaybı, A3'te veri taşıma. (b) Üretici bilgisini hiç göstermemek — reddedildi: ürünün ayırt edici değeri kaybolur. (c) A3 kararı gelene kadar beklemek — reddedildi: araç zincirinin kanıtlanması ticari karara bağlanamaz.

**Kabul edilen maliyet:** `V2` migration'ı `sellers` tablosunu genişletecek. Bu, `00-current-state.md` bilinen sınırlamalar tablosuna yazılır.

### D5 · Geliştirme verisi dev profiline bağlı idempotent seed ile gelir

Örnek üretici ve ürünler, yalnızca `dev` profilinde çalışan ve `INSERT ... ON CONFLICT DO NOTHING` ile yazan bir Flyway `R__` betiğiyle yüklenir. Üretim profilinde çalışmaz.

**Gerekçe:** `AGENTS.md` K6 hardcode mock veriyi yasaklıyor ama "geliştirme verisi gerekiyorsa idempotent seed" diyor. Vitrinin boş olmadığı bir ekran olmadan dört durum (yükleniyor/boş/hata/yetkisiz) doğrulanamaz. Veri sahtedir ve K10'a uyar (`ornek.test`, gerçek üretici adı yok).

**Reddedilen alternatifler:** (a) Testlerde ve ekranda sabit kodlanmış örnek dizi — reddedildi: K6 ihlali. (b) Seed'i üretimde de çalıştırmak — reddedildi: üretim verisine sahte kayıt girer.

### D6 · Sözleşme tazeliği ayrı bir testle zorunludur

`api` test paketinde bir test `/v3/api-docs.yaml` çıktısını üretir ve `shared/contracts/openapi.yaml` ile **birebir** karşılaştırır; fark varsa kırmızıdır. Aynı test `-Dopenapi.write=true` ile çalıştırıldığında dosyayı günceller.

**Gerekçe:** [ADR-009](../../../docs/adr/009-contract-generated-from-spring.md) sözleşmenin üretildiğini ve commit'lendiğini söylüyor; ikisinin ayrışmasını engelleyen tek mekanizma bu testtir. SafeZone'daki `generate_config_docs.py --check` deseninin karşılığıdır.

**Reddedilen alternatif:** Sözleşmeyi her derlemede sessizce üzerine yazmak. Reddedildi: değişiklik PR diff'inde fark edilmeden geçer, kırıcı değişiklik gözden kaçar.

## Mevcut ADR'lerden türeyen, yeni karar içermeyen detaylar

| Detay | Dayanak |
|---|---|
| Para `{"amount": <kuruş>, "currency": "TRY"}`; tutar sunucuda hesaplanır | [ADR-006](../../../docs/adr/006-money-as-minor-unit.md) |
| Şema yalnızca Flyway ile; `ddl-auto: validate` | [ADR-004](../../../docs/adr/004-postgresql-with-flyway.md) |
| Repository testleri Testcontainers PostgreSQL ile; iş kuralı testleri `critical` etiketli | [ADR-005](../../../docs/adr/005-test-strategy.md) |
| Tek hata gövdesi (`code`, `message`, `fieldErrors`), liste yanıt şekli, durum kodları | `AGENTS.md` §11.5 |
| Paket düzeni domain'e göre (`com.bahceden.catalog`), controller ince, iş kuralı service'te | `AGENTS.md` §11.2 |
| `web` tipleri `shared/generated` üretiminden; API tipi elle yazılmaz | [ADR-003](../../../docs/adr/003-openapi-as-single-contract.md), [ADR-009](../../../docs/adr/009-contract-generated-from-spring.md) |
| Dört durum (yükleniyor/boş/hata/yetkisiz), mobil önce, CSS Modules + token | `AGENTS.md` §12.4, §12.5 |

## Risks / Trade-offs

| Konu | Değerlendirme |
|---|---|
| `sellers` tablosunun erken doğması | D4'te gerekçeli; `V2` genişletmesi kabul edilmiş maliyet |
| Testcontainers CI süresi | İlk dilimde tek repository testi; süre artarsa `critical` / `integration` ayrımı devreye girer ([ADR-005](../../../docs/adr/005-test-strategy.md)) |
| Üretilen sözleşmenin şema adları | `@Schema(name = ...)` ile domain adlarına sabitlenir; sözleşme testi alan adlarını kilitler |
| Kimlik olmadan açık uç nokta | Vitrin zaten herkese açık olacak; uç nokta `permitAll` olarak **açıkça** işaretlenir (varsayılan deny korunur, `AGENTS.md` §11.6) |

## Migration Plan

1. `V1__create_catalog.sql`: `sellers` ve `products` tabloları, `products.status` üzerinde kısmi indeks (`status = 'PUBLISHED'`), `products.slug` ve `sellers.slug` üzerinde tekil indeks.
2. `R__dev_seed_catalog.sql`: yalnızca `dev` profilinde, idempotent (D5).
3. Geri alma: bu dilim yeni tablo eklediği için geri alma `V1`'i düşürmek değil, tabloları bırakan yeni bir migration yazmaktır ([ADR-004](../../../docs/adr/004-postgresql-with-flyway.md)); pilot öncesi veri olmadığı için pratik risk yok.

## Open Questions

Bu dilimi bloke **etmeyen**, ama sonraki dilimlerde karara bağlanacak konular:

- Ürün birimi (`kg`, `adet`, `demet`) serbest metin mi, enum mu? Bu dilimde enum olarak başlıyor (`KILOGRAM`, `PIECE`, `BUNCH`); listenin büyümesi bir sonraki dilimde değerlendirilir.
- Vitrinin sıralaması uzun vadede "yeniden eskiye" mi kalacak, yoksa alaka/öne çıkarma mı olacak? Arama diliminde karara bağlanır.
- Ürün görseli olmadan vitrinin kabul edilebilirliği: KALANLAR B3 kararına kadar liste görselsiz; tasarım bunu yer tutucu bir blokla değil, tipografiyle çözer.

# [ADR-013] `sellers` tablosu, satıcı onboarding kararından önce bilinçli olarak minimum doğar

**Tarih:** 2026-10-06
**Durum:** Accepted
**Kaynak:** `add-storefront-product-listing` change, `design.md` D4

---

## Context

Vitrinin ürün değeri, ürünü **üreticisiyle** göstermekten geçiyor: "tazeyi, kimden geldiğini bilerek almak" (`docs/product/index.md`). Üretici adı olmayan bir liste, ürünü sıradan bir e-ticaret listesine indirger.

Ancak satıcı onboarding kararı açık (`KALANLAR.md` A3): üretici nasıl doğrulanacak (vergi levhası, çiftçi kayıt belgesi), onayı kim verecek, ilan ön onaydan geçecek mi? Bu karar `sellers` tablosunun gerçek şeklini belirleyecek ve yasal sorumluluk taşıyor.

İlk dikey dilimin amacı araç zincirini kanıtlamak; bu amacın ticari bir karara bağlanması dilimi süresiz bloklardı.

## Decision

`sellers` tablosu bu aşamada yalnızca **vitrinin ihtiyaç duyduğu** alanları taşır: `id`, `slug`, `display_name`, `city`, zaman damgaları.

**Bilinçli olarak yoktur:** hesap ilişkisi (`account_id`), vergi ve belge alanları, onay durumu, iletişim bilgisi, banka/IBAN bilgisi. Bunlar A3 kararıyla birlikte ayrı bir migration olarak gelecektir.

Bu, kabul edilmiş bir maliyettir ve `docs/reference/00-current-state.md` bilinen sınırlamalar tablosunda izlenir.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Ürün üzerinde serbest metin `producer_name` alanı | Normalleştirme kaybı: aynı üretici farklı yazımlarla çoğalır; A3 geldiğinde veri taşıma ve eşleştirme işi doğar. |
| Üretici bilgisini hiç göstermemek | Ürünün ayırt edici değeri kaybolur; vitrin, değer önerisini anlatmayan bir liste olur. |
| A3 kararı gelene kadar beklemek | Araç zincirinin kanıtlanması ticari bir karara rehin olurdu; dilim süresiz bloklanırdı. |
| Tabloyu şimdiden tüm olası alanlarla açmak | Karar verilmeden alan tasarlamak, yanlış alanları kalıcı hale getirir (`AGENTS.md` §3). Boş kalan alanlar da yanlış veri kadar zararlıdır. |

## Consequences

### Olumlu
- Vitrin ürünün gerçek değerini gösteriyor ve ilk dilim ticari karara bağlı kalmıyor.
- Tablo küçük olduğu için genişletmek kolay: eklenecek alanlar `NULL` kabul eden yeni kolonlar olarak gelir.

### Olumsuz
- A3 kararından sonra kesinlikle bir `V2` migration'ı gerekecek; `sellers` tablosu iki adımda şekillenecek.
- Bu dilimde üretici kaydı **yalnızca** geliştirme seed'i ile oluşur; satıcıların kendi kaydını açtığı bir akış yok.
- Üretici ile hesap arasında ilişki olmadığı için satıcı yetkilendirmesi ([ADR-007](007-query-level-ownership-scoping.md)) bu dilimde uygulanamıyor; yalnızca okuma ucu var.

### Nötr / Trade-off
- Tablo adı `sellers` (satıcı) ama ürün dili "üretici" diyor: kod tanımlayıcıları İngilizce ve alan adı rolü anlatır, kullanıcıya görünen metin Türkçe ve "üretici" der (`AGENTS.md` §1).

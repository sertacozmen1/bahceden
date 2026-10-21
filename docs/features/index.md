# Feature Kayıtları

> **Amaç:** "Bu feature ne yapar, neden böyle, ne zaman ne değişti?" sorusuna **tek dosyadan** cevap vermek.
> Her feature'ın yaşayan bir dosyası vardır; her iş bittiğinde ilgili dosyanın **Tarihçe** bölümüne bir satır eklenir.

---

## Bu klasör ne değildir?

| Soru | Nereye bakılır |
|---|---|
| Bu karar **neden** alındı, alternatifler neydi? | [ADR](../adr/index.md) |
| Sistem **şu an tam olarak** nasıl davranmalı? (gereksinim + senaryo) | `openspec/specs/<capability>/spec.md` |
| Bu değişiklik **nasıl planlandı ve uygulandı**? (proposal / design / tasks) | `openspec/changes/archive/<tarih>-<ad>/` |
| Sistemin **mevcut yapısı** (ER, akış, API, tasarım sistemi) | [Referans](../reference/00-current-state.md) |
| Bu işi **nasıl yaparım**? | [How-to](../how-to/index.md) |
| **Bu feature'ın hikâyesi**: ne yapar, hangi kararlara dayanır, zaman içinde ne değişti? | **Burası** |

Feature dosyası içerik üretmez; yukarıdakilere **link verir** ve zaman içindeki değişimi **kronolojik** tutar.

---

## Feature listesi

| Feature | Durum | İlk sürüm | Son değişiklik | Kapsadığı capability'ler |
|---|---|---|---|---|
| [Vitrin ve Arama](vitrin-ve-arama.md) | Yayında | 2026-10-06 | 2026-10-08 | storefront-catalog |
| [Hesap ve Oturum](hesap-ve-oturum.md) | Yayında | 2026-10-07 | 2026-10-21 | account-identity |
| [Satıcı Kaydı ve Onayı](satici-kaydi-ve-onayi.md) | Yayında | 2026-10-08 | 2026-10-08 | seller-onboarding |
| [Ürün İlanları](urun-ilanlari.md) | Yayında | 2026-10-08 | 2026-10-08 | product-listings |
| [Sepet ve Sipariş](sepet-ve-siparis.md) | Yayında | 2026-10-08 | 2026-10-20 | cart-and-checkout, orders |
| [Ödeme ve Hakediş](odeme-ve-hakedis.md) | Yayında | 2026-10-08 | 2026-10-14 | seller-payout-details |
| [Kargo ve Teslimat](kargo-ve-teslimat.md) | Yayında | 2026-10-10 | 2026-10-19 | shipments, orders |
| [Ayıplı Ürün ve İade](ayipli-urun-ve-iade.md) | Yayında | 2026-10-14 | 2026-10-19 | defect-claims, orders |
| [Genel: feature'a bağlı olmayan işler](_genel.md) | - | - | 2026-10-08 | (dokümantasyon, araçlar, altyapı, AI kuralları) |

**Planlanan feature'lar** (ilk dilimlerle birlikte dosyaları açılacak):

| Feature | Kapsam | Bloke eden karar |
|---|---|---|
| Bildirimler | Sipariş/kargo e-postası, satıcı bildirimi | KALANLAR B4 |

**Durum değerleri:** `Fikir` (yalnızca product belgesi var) · `Geliştirmede` (change açık) · `Yayında` (change archive edildi, `main`'de) · `Kaldırıldı`.

---

## Kurallar

1. **Her iş bir satır bırakır.** OpenSpec'ten geçsin geçmesin, büyük küçük her iş bittiğinde ilgili feature dosyasının Tarihçe bölümüne **tek satır** eklenir: `- YYYY-MM-DD: ne değişti (ADR / change / PR referansı)`. Feature'a bağlanmayan işler (araç, doküman, altyapı, kural dosyaları) [`_genel.md`](_genel.md) dosyasına yazılır.
2. **Tarihçe append-only'dir; yeni satır en üste.** Eski satır düzenlenmez, silinmez. Bir davranış geri alındıysa bu da yeni bir satırdır.
3. **Feature = ürün seviyesi.** Kullanıcının adlandıracağı büyüklükte; teknik `capability`'ler feature dosyasının içinde listelenir, ayrı dosya olmaz.
4. **Yeni feature = yeni dosya + tabloya satır.** [`template.md`](template.md) kopyalanır, dosya adı Türkçe kebab-case (`urun-ilanlari.md`), bu tabloya ve `docs/.tools/mkdocs.yml` nav bölümüne eklenir.
5. **"Ne yapar" bölümü kısa ve kullanıcı diliyle.** Gereksinim listesi değil (o spec'tedir); 3–6 cümle.
6. **Durum ve "Son değişiklik" bu tabloda da güncellenir.** Feature dosyasındaki başlık alanlarıyla tutarlı olmalıdır.

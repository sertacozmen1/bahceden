# Bahçeden: Dokümantasyon Merkezi

> **Single Source of Truth** teknik dokümantasyon portalı.
> Bu klasör, Bahçeden projesinin tüm teknik bilgisini barındırır. Bağlayıcı kurallar için depo kökündeki `AGENTS.md` dosyasına bakın.

**Nereden başlamalı?** Projeye yeni katıldıysanız sırayla: [Güncel Durum](reference/00-current-state.md) → `AGENTS.md` → [ADR listesi](adr/index.md) → [Yerel geliştirme](how-to/yerel-gelistirme.md).

---

## Yeni bir doküman nereye eklenmeli? (Karar ağacı)

### 1. Karar alma ve araştırma (kodlamadan önce)

- **`product/`** — Kullanıcıya dokunan yeni bir özellik veya modül planlıyorsanız: problem, değer önerisi, rakip analizi, kabul kriterleri.
  📝 Şablon: `product/template.md`
- **`research/`** — Yeni bir kütüphane, sağlayıcı (ödeme, kargo, SMS) veya yaklaşım denediyseniz: fizibilite, ölçüm, karşılaştırma.
  📝 Şablon: `research/template.md`
- **`adr/`** — Mimarîde tüm ekibi etkileyecek, geri dönüşü maliyetli yapısal bir karar aldıysanız (örn. "GraphQL yerine OpenAPI").
  📝 Şablon: `adr/template.md` · 📋 Liste: [ADR index](adr/index.md)

### 2. Rehberler ve eğitimler (aksiyon odaklı)

- **`how-to/`** — Sınırları belli bir görevi adım adım çözen rehberler (örn. "yeni API ucu ekleme", "migration geri alma").
  📝 Şablon: `how-to/template.md` · 📋 Liste: [How-to index](how-to/index.md)
- **`tutorials/`** — Sistemi hiç bilmeyen birine sıfırdan öğreten geniş eğitim serileri.

### 3. Teknik referans ve açıklamalar (bilgi odaklı)

- **`reference/`** — Sistemin mevcut halinin dökümü: güncel durum, ER diyagramı, akış diyagramları, API referansı, tasarım sistemi. Gerçekliğin birebir yansıması; plan değil.
- **`explanation/`** — Belirli bir tasarımın **neden** öyle olduğunu anlatan kavramsal yazılar (ADR'den daha uzun soluklu, karar kaydı değil).

### 4. Yapılan işin kaydı (kodlamadan sonra)

- **`features/`** — Her feature'ın **yaşayan dosyası**: ne yapar, hangi ADR/spec/change'e dayanır, zaman içinde ne değişti. Büyük küçük **her iş** bittiğinde ilgili dosyanın *Tarihçe* bölümüne bir satır eklenir; feature'a bağlı olmayan işler [`features/_genel.md`](features/_genel.md) dosyasına yazılır.
  📝 Şablon: `features/template.md` · 📋 Liste: [Feature index](features/index.md)

---

## Klasör yapısı

```text
docs/
├── README.md          ← 📍 Buradasınız
├── product/           ← Ürün stratejisi, özellik tanımları (müşteri odaklı)
├── research/          ← Kütüphane / sağlayıcı değerlendirmeleri (veri odaklı)
├── adr/               ← Mimari karar kayıtları (sistem odaklı, bağlayıcı)
├── reference/         ← Teknik referans: güncel durum, ER, akış, API
├── how-to/            ← Nasıl yapılır rehberleri
├── tutorials/         ← Başlangıç eğitimleri
├── explanation/       ← Kavramsal açıklamalar
├── features/          ← Feature kayıtları: ne yapar, ne zaman ne değişti (Tarihçe)
└── .tools/            ← MkDocs portalı (uygulama bağımlılığı değildir)
```

---

## Dokümantasyon portalı

```bash
./docs/.tools/serve.sh            # http://localhost:8888
./docs/.tools/serve.sh --build    # strict derleme (CI ile aynı kontrol)
```

Her yeni sayfa `docs/.tools/mkdocs.yml` içindeki `nav` bölümüne eklenir ve `mkdocs build --strict` geçmelidir. Link hedefleri `docs/` içinde kalır: dışarıya (depo köküne, koda) markdown link verilmez, kod span'ı kullanılır.

---

## Kurallar

- **Dil:** Dokümanlar Türkçe. Kod tanımlayıcıları, API alanları ve commit mesajları İngilizce (`AGENTS.md` §1).
- **Kanıt:** "Şu an ne var?" sorusunun tek cevabı [`reference/00-current-state.md`](reference/00-current-state.md); plan belgeleri bu soruya cevap olarak kullanılamaz.
- **Her iş iz bırakır:** OpenSpec'ten geçmeyen küçük bir iş bile [`features/`](features/index.md) *Tarihçe* satırını bırakır.
- **Secret yok:** Hiçbir dokümanda gerçek anahtar, parola, müşteri verisi veya üretim bağlantı bilgisi bulunmaz (`AGENTS.md` K10). Örnekler placeholder değer kullanır.

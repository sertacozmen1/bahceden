# Araştırma (Research)

> Yeni bir kütüphane, dış sağlayıcı (ödeme, kargo, SMS, nesne depolama) veya yaklaşım denendiğinde bulgular buraya yazılır.

Araştırma notu bir **karar değildir**: karar [ADR](../adr/index.md) altına yazılır. Araştırma, kararın dayandığı ölçümü ve karşılaştırmayı tutar. Bu bölümdeki bir not hiçbir zaman "sistem şu an böyle çalışıyor" kanıtı olarak kullanılamaz (`AGENTS.md` §5.4).

**Bağımlılık bağlantısı:** Bağımlılık bütçesi dışına çıkan her paket ve servis için ([ADR-002](../adr/002-dependency-budget.md)) araştırma notu, `AGENTS.md` K7'deki dört sorunun cevabını içermelidir: platform neden yetmiyor, iki alternatif ve ret gerekçeleri, yük analizi (boyut, transitive bağımlılık, lisans, bakım durumu, güvenlik yüzeyi), hangi katmana ekleniyor.

---

## Notlar

| Konu | Durum | Sonuç |
|---|---|---|
| (henüz yok) | - | - |

## Beklenen araştırma başlıkları

`openspec/KALANLAR.md` maddeleri karara bağlanmadan önce ölçüm gerektiren konular:

| Başlık | İlgili madde |
|---|---|
| Ödeme sağlayıcıları karşılaştırması: pazaryeri (alt üye işyeri) desteği, komisyon, entegrasyon yükü, iade akışı | A1 |
| Kargo firması entegrasyonları: API olgunluğu, barkod üretimi, takip bildirimi, bozulabilir ürün politikası | A2 |
| Ürün fotoğrafı depolama: yerel disk + volume ile S3 uyumlu nesne depolama karşılaştırması | B3 |
| SMS sağlayıcısı: maliyet, teslim oranı, Türkiye operatör kapsaması | B4 |
| Üretim barındırma: tek VPS + Compose ile yönetilen platform karşılaştırması, yedekleme | B5 |

Yeni not `template.md` dosyasından kopyalanır, bu tabloya ve `docs/.tools/mkdocs.yml` nav bölümüne eklenir.

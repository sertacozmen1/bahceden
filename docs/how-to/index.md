# Nasıl Yapılır Rehberleri (How-to Guides)

> **Diátaxis kategorisi:** How-to Guides; *görev odaklı, sınırları belli problem çözüm rehberleri.*

Bu bölüm, Bahçeden'de **belirli bir görevi tamamlamak** isteyen geliştirici ve operatörler için pratik rehberler barındırır. Kavramsal anlatım için [Açıklamalar](../explanation/index.md), sistemin mevcut halinin dökümü için [Referans](../reference/00-current-state.md) bölümüne bakın.

---

## Rehberler

| # | Başlık | Kategori | Durum |
|---|--------|----------|-------|
| 1 | [Yerel geliştirme ortamını kurma](yerel-gelistirme.md) | Geliştirme | Hazır |
| 2 | AI asistanla Bahçeden'de geliştirme | Geliştirme | Planlanıyor |
| 3 | Testleri çalıştırma | Geliştirme | İlk dilimle |
| 4 | [Yeni API ucu ekleme (sözleşmeden ekrana)](yeni-api-ucu-ekleme.md) | Geliştirme | Hazır |
| 10 | [Korunan bir API ucu ekleme](korunan-uc-ekleme.md) | Geliştirme | Hazır |
| 5 | Veritabanı migration'ı yazma ve geri alma | Veri | İlk dilimle |
| 6 | Yeni ortam değişkeni ekleme | Operasyon | İlk dilimle |
| 7 | Üretim ortam değişkenleri ve güvenlik | Operasyon | Planlanıyor |
| 8 | Satıcı başvurusunu elle onaylama | Operasyon | KALANLAR A3'e bağlı |
| 9 | Kargo gönderisi oluşturma ve takip | Operasyon | KALANLAR A2'ye bağlı |

**Durum değerleri:** `Hazır` · `İlk dilimle` (ilgili kod yazıldığında aynı change içinde yazılacak) · `Planlanıyor` · `KALANLAR X'e bağlı` (karar bekliyor).

---

## Üslup

Yeni rehber [`template.md`](template.md) dosyasından kopyalanır ve şu üslubu izler:

- Kısa giriş: bu rehber kimin hangi işini çözüyor.
- **Ön koşullar**: hangi araç, yetki ve önceden okunmuş doküman gerekiyor.
- Numaralı adımlar; her adımda **gerçek, çalıştırılabilir komut** (uydurma komut yazılmaz).
- `> [!NOTE]` ve `> [!TIP]` blokları ile uyarılar.
- **Olası hatalar**: hata metni + çözümü.
- İlgili [ADR](../adr/index.md) referansları.

> [!TIP]
> Yeni rehber eklerken bu tablodaki numarayı sıradan devam ettirin (sıralı olmaları gerekmez) ve sayfayı `docs/.tools/mkdocs.yml` nav bölümüne ekleyin; `mkdocs build --strict` geçmelidir.

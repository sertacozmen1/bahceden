# Ürün ve Strateji

> **Diátaxis dışı, ürün odaklı bölüm.** Kullanıcıya dokunan özelliklerin **neden** yapıldığını, kime ne değer kattığını ve "tamamlandı" sayılma koşullarını kaydeder.

Bu bölüm teknik tasarım içermez: "nasıl" sorusu [ADR](../adr/index.md) ve `openspec/specs/` altındadır.

---

## Ürün tanımı

Bahçeden, meyve-sebze üreticisini son tüketiciyle **doğrudan** buluşturan çok satıcılı bir pazaryeridir. Manisa'daki bir üreticinin, bir hırdavatçının büyük pazaryerlerine kaydolduğu kolaylıkla kaydolup ürününü listeleyebilmesi ve **kargo ile** göndermesi hedeflenir. Hal, komisyoncu ve aradaki fiyat katmanları zincirden çıkar.

**Kim için?**

| Kullanıcı | Ne istiyor | Bugünkü acısı |
|---|---|---|
| Küçük üretici (satıcı) | Ürününü hak ettiği fiyata, aracısız satmak | Hale verdiği fiyat ile rafta görülen fiyat arasındaki uçurum; kendi kanalını kuramıyor |
| Son tüketici (alıcı) | Tazeyi, kimden geldiğini bilerek, makul fiyata almak | Zincirde kimin ne kattığı belirsiz; tazelik ve kaynak izlenemiyor |

## Ürün belgeleri

| Belge | Durum |
|---|---|
| [Satıcı Kaydı ve Onayı](satici-kaydi.md) | Yayında |
| [Ürün İlanları](urun-ilanlari.md) | Yayında |
| [Sepet ve Sipariş](sepet-ve-siparis.md) | Yayında |
| [Kargo ve Teslimat](kargo-ve-teslimat.md) | Yayında |
| [Ayıplı Ürün İadesi](ayipli-urun-iadesi.md) | Geliştirmede |

İlk ürün belgeleri, `openspec/KALANLAR.md` A1–A4 maddeleri (ödeme, kargo, satıcı onboarding, kapsam) karara bağlandıkça yazılacaktır. Yeni belge `template.md` dosyasından kopyalanır ve bu tabloya eklenir.

## Bilinçli kapsam dışı (bugün)

- Çoklu dil ve çoklu para birimi (KALANLAR A4).
- Mobil uygulama; web mobil uyumlu olacak ama ayrı uygulama yol haritasında değil.
- Üretici-üretici (B2B) toptan satış; hedef son tüketici.
- Abonelik kutusu modeli; tekil sipariş akışıyla başlanır.

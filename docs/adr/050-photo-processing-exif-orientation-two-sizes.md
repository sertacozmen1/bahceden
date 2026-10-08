# [ADR-050] Fotoğraf işleme: başlıktan piksel sınırı, EXIF yönü, iki boyut, JPEG yeniden kodlama; orijinal saklanmaz

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `add-product-listings` design.md D4

---

## Context

[ADR-039](039-uploads-on-local-volume-served-by-api.md) fotoğrafların sunucuda ImageIO ile boyutlandırılmasını ve EXIF temizlenmesini istedi. Telefon fotoğrafları yönünü piksellerde değil EXIF etiketinde taşır ve ImageIO bu etiketi yok sayar. Çok büyük piksel boyutlu bir dosya belleği tüketebilir (görüntü bombası).

## Decision

İşlem yalnızca JDK ile, `PhotoProcessor` içinde yapılır:

1. Tür dosyanın ilk baytlarından tespit edilir (JPEG, PNG); boyut sınırı 10 MB.
2. Piksel boyutu `ImageReader` ile yalnızca başlıktan okunur; görüntü belleğe açılmadan önce 40 megapiksel sınırı denetlenir.
3. JPEG'in APP1 bölümündeki yön etiketi (`0x0112`) küçük bir okuyucuyla (`ExifOrientation`) elle okunur ve sekiz yönün hepsi açık dönüşüm matrisleriyle uygulanır.
4. Saydamlık beyaz zemine düzleştirilir.
5. Uzun kenarı 400 px (kart) ve 1600 px (büyük) iki çıktı üretilir; büyük oranlarda adım adım yarıya inilir. Çıktılar JPEG, kalite 0,85.
6. Yeniden kodlama bütün meta veriyi (EXIF, konum) atar. Orijinal dosya saklanmaz.

Okunamayan dosya (bozuk, CMYK JPEG) `400` döner ve `fieldErrors[photo]` içinde gelir.

| Alternatif | Neden reddedildi |
|---|---|
| Görüntü / meta veri kütüphanesi (Thumbnailator, metadata-extractor) | K7 dışında; tek bir etiket için gereksiz. |
| Orijinali de saklamak | Konum gibi meta veri diskte kalır; bugün "orijinali indir" ihtiyacı yok. |
| Boyutlandırmayı `next/image`'a bırakmak | Büyük orijinal diskte kalır, EXIF temizlenmez (ADR-039'un reddettiği durum). |

## Consequences

### Olumlu
- Telefon fotoğrafları doğru yönde görünür. Bu, testte ve uçtan uca senaryoda yön etiketli bir JPEG ile doğrulandı.
- Sunulan dosyada EXIF ve konum bulunmaz.
- Görüntü bombası belleğe açılmadan reddedilir.

### Olumsuz
- ImageIO'nun desteklemediği JPEG çeşitleri reddedilir.
- Görüntü işleme istek içinde, eşzamanlı yapılır ve CPU kullanır.

### Nötr / Trade-off
- Orijinal saklanmadığı için daha büyük bir boyut ihtiyacı ileride doğarsa eski fotoğraflar için üretilemez.

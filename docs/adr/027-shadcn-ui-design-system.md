# [ADR-027] Tasarım sistemi: shadcn/ui (Base UI) + Tailwind v4

**Tarih:** 2026-10-07
**Durum:** Accepted · [ADR-002](002-dependency-budget.md)'nin `web/` bütçesini genişletir ve oradaki "Tailwind CSS veya UI kütüphanesi" reddini bu kapsamda kaldırır

---

## Context

`web/` ilk iki dilimde CSS Modules ve tek bir token dosyasıyla yazıldı ([ADR-002](002-dependency-budget.md)). Bugün yedi ekran var (vitrin, kayıt, giriş ve ikinci adım, doğrulama, hesabım, hata ve bulunamadı durumları). Sıradaki dilimler satıcı paneli, ürün ekleme formu, sepet ve sipariş ekranlarını getirecek. Bu ekranlar diyalog, açılır menü, seçim kutusu ve bildirim gibi bileşenlere ihtiyaç duyacak; odak tuzağı, klavye gezintisi ve ekran okuyucu davranışı elle yazıldığında hem yavaş hem hatalı olur.

Proje sahibi Next.js ekosisteminde en yaygın tasarım altyapısının kullanılmasını istedi ve önerilen paket listesini onayladı (2026-10-07). Araştırma bulguları:

- shadcn/ui bileşen kodunu pakete değil projeye kopyalar; kod ekibin olur, sürüm kilidi oluşmaz.
- State of React anketinde kullanımı bir yılda %20'den %42'ye çıktı ve memnuniyette %80 ile ilk sırada. Toplam kullanımda MUI hâlâ önde.
- shadcn/ui'nin güncel varsayılan stili (`base-nova`) Radix yerine Base UI primitive'lerini kullanır.

## Decision

1. **Stil altyapısı Tailwind CSS v4.** CSS Modules kaldırıldı. Tasarım token'ları `web/src/app/globals.css` içinde shadcn'in anlamsal adlarıyla (`--primary`, `--muted`, `--destructive` …) OKLCH olarak tanımlanır, `@theme inline` ile Tailwind sınıflarına eşlenir. Projeye özgü tek ek token `--warning`'dir (az kalan stok).
2. **Bileşenler shadcn/ui `base-nova` stilinden kopyalanır** ve `web/src/components/ui/` altında yaşar: `button`, `input`, `label`, `field`, `card`, `badge`, `alert`, `separator`, `skeleton`. Kopyalanan kod projenin kodudur. Varsayılan halde bırakılmaz:
   - Dokunma hedefi: düğme ve girdi mobilde 44px, masaüstünde 40px. Satıcı paneli sahada telefonla kullanılacak (AGENTS.md §12.4).
   - Renk, köşe ölçeği ve tipografi projenin temasına göre ayarlandı.
   - Erişilebilirlik sözleşmesi korundu. Alan hatası `role="alert"` taşımaz; yalnızca formun genel hatası taşır.
3. **Primitive katmanı Base UI** (`@base-ui/react`). Radix ile aynı anda kullanılmaz.
4. **İkon seti Lucide** (`lucide-react`). shadcn bileşenlerinin yerleşik setidir. Çizgi kalınlığı global olarak 1,75. Elle SVG ikon çizilmez.
5. **Yazı tipi Geist**, `next/font` ile derleme anında indirilir ve kendi sunucumuzdan verilir. `latin-ext` alt kümesi Türkçe harfler için zorunlu. Tarayıcı çalışma anında üçüncü bir alan adına gitmez.
6. **Tema** işletim sistemi tercihini izler (`prefers-color-scheme`). Elle tema değiştirici yok; `next-themes` bütçede değil.
7. **Palet "Orman":** koyu yaprak yeşili marka rengi, yeşile çalan kırık beyaz zemin, kehribar yalnızca anlamsal uyarı rengi. Saf beyaz ve saf siyah kullanılmaz.
8. **Paket listesi** (tamamı tam sürümle pin'li):

   | Paket | Sürüm | Katman | Lisans |
   |---|---|---|---|
   | `@base-ui/react` | 1.8.0 | `web/` runtime | MIT |
   | `class-variance-authority` | 0.7.1 | `web/` runtime | Apache-2.0 |
   | `clsx` | 2.1.1 | `web/` runtime | MIT |
   | `tailwind-merge` | 3.7.0 | `web/` runtime | MIT |
   | `lucide-react` | 1.52.0 | `web/` runtime | ISC |
   | `tailwindcss` | 4.3.3 | `web/` dev | MIT |
   | `@tailwindcss/postcss` | 4.3.3 | `web/` dev | MIT |
   | `tw-animate-css` | 1.4.0 | `web/` dev | MIT |

9. **shadcn'in iki önerisi bilinçli olarak alınmadı:**
   - `cn` paketi (0.4.0): `cn()` yardımcısı `web/src/lib/utils.ts` içinde `clsx` + `tailwind-merge` ile yazıldı.
   - `shadcn` paketinin `tailwind.css` dosyası: kullandığımız özel varyantlar (`data-open`, `data-horizontal` …) `globals.css` içine kopyalandı.

   Gerekçe: 0.x sürümlü bir pakete ve CLI paketine çalışma zamanı bağımlılığı kurmamak. `shadcn` CLI yalnızca `npx` ile çalışır, `package.json`'a girmez.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| CSS Modules ile devam, bileşenleri elle yazmak | Erişilebilir diyalog, menü ve seçim kutusunu elle yazmak satıcı paneli dilimini yavaşlatır ve hata yüzeyi bırakır. |
| MUI | Kendi tasarım dilini dayatır; ağır, çalışma anı CSS-in-JS ile Server Component modeline uymuyor. |
| Radix Themes | Hazır görünüm, ama özelleştirmek kopyalanan koddan zor; tema bir paketin içinde kalır. |
| shadcn/ui + Radix primitive'leri | Çalışır ve örneği bol; shadcn'in yeni varsayılanı Base UI olduğu için yeni kurulumda eski yol seçilmedi. |
| Phosphor ikonları | Daha karakterli, ama son sürümü 2025-05 ve shadcn bileşenlerindeki ikonlar elle değiştirilmek zorunda kalırdı. |

## Consequences

### Olumlu
- Satıcı paneli ve sipariş ekranları için erişilebilir primitive'ler hazır; diyalog ve menü gibi bileşenler `npx shadcn add` ile eklenip uyarlanabilir.
- Tek token dosyası korunuyor: renk ve köşe ölçeği tek yerde değişir, açık ve koyu tema aynı anlamsal adları paylaşır.
- Bileşen kodu projede: sürüm yükseltmesi zorunlu değil, değişiklik diff'te görünür.

### Olumsuz
- `web/` runtime bağımlılığı 3 paketten 8 pakete çıktı. İstemciye giden JS büyüdü; Base UI ve Lucide yalnızca kullanılan modülleri paketler.
- Tailwind sınıfları uzun; okunabilirlik için varyantlar `cva` ile bileşende toplanır, sayfalar bileşen kullanır.
- `npx shadcn add` bileşenleri `cn` paketinden içe aktarır ve `shadcn` paketini ekler. Her eklemede içe aktarma yolu `@/lib/utils` olarak düzeltilir ve `package.json` değişikliği geri alınır ([UI bileşeni ekleme](../how-to/ui-bileseni-ekleme.md)).
- `next/font` derleme sırasında Google Fonts'a erişim ister; ağsız derleme ortamında derleme düşer.

### Nötr / Trade-off
- Kopyalanan kod yukarı akıştaki düzeltmeleri kendiliğinden almaz. Bu, sürüm kilidi olmamasının bedelidir.
- Elle tema değiştirici yok. İhtiyaç doğarsa `next-themes` ayrı bir K7 kararıdır.
- Ürün fotoğrafı yok (KALANLAR B3); vitrin kartı tipografiye ve fiyata yaslanır, sahte görsel kullanılmaz.

# Tasarım Sistemi

**Son doğrulama:** 2026-10-07 · Karar: [ADR-027](../adr/027-shadcn-ui-design-system.md) · Ekleme rehberi: [UI bileşeni ekleme](../how-to/ui-bileseni-ekleme.md)

> Bu sayfa `web/` arayüzünün **mevcut** tasarım sisteminin dökümüdür. Kaynak kod esastır: token'lar `web/src/app/globals.css`, bileşenler `web/src/components/ui/`. Kod değiştiğinde bu sayfa aynı commit'te güncellenir.

---

## 1. Yön

| | |
|---|---|
| **Okuma** | Üreticiden doğrudan alan sıradan alıcı ve sahada telefonla çalışan satıcı için güven odaklı pazaryeri |
| **Ayarlar** | Düzen çeşitliliği 4 · Hareket 3 · Yoğunluk 5 (sakin, öngörülebilir, telefonda rahat) |
| **Altyapı** | Tailwind CSS v4 + shadcn/ui `base-nova` (Base UI primitive'leri) + Lucide ikonları |
| **Yazı tipi** | Geist (gövde ve başlık), Geist Mono (giriş kodu); `next/font`, `latin` + `latin-ext` |
| **Tema** | İşletim sistemi tercihi (`prefers-color-scheme`); elle değiştirici yok |
| **Hareket** | Yalnızca geri bildirim: düğmeye basınca 1px çökme, iskelette nabız (`motion-reduce` ile durur) |

## 2. Renk token'ları

Anlamsal adlar shadcn/ui sözleşmesidir; bileşen ve sayfalar yalnızca bunları kullanır (`bg-primary`, `text-muted-foreground` …).

| Token | Açık | Koyu | Kullanım |
|---|---|---|---|
| `--background` | `oklch(0.982 0.005 130)` | `oklch(0.17 0.012 155)` | Sayfa zemini (yeşile çalan kırık beyaz / koyu orman) |
| `--foreground` | `oklch(0.22 0.025 152)` | `oklch(0.95 0.008 130)` | Ana metin |
| `--card` | `oklch(0.997 0.002 130)` | `oklch(0.205 0.015 155)` | Kart, form paneli, girdi zemini |
| `--primary` | `oklch(0.45 0.105 150)` | `oklch(0.76 0.13 148)` | Marka yeşili: birincil düğme, bağlantı, marka işareti |
| `--secondary` | `oklch(0.95 0.022 145)` | `oklch(0.27 0.025 152)` | İkincil düğme, "Stokta" etiketi, başarı kutusu |
| `--muted` / `--muted-foreground` | `0.955` / `0.47` | `0.25` / `0.73` | İskelet, ikincil metin (AA kontrastı korunur) |
| `--destructive` | `oklch(0.53 0.19 27)` | `oklch(0.7 0.17 25)` | Hata metni ve kutusu, "Stokta yok" |
| `--warning` | `oklch(0.52 0.12 62)` | `oklch(0.8 0.13 75)` | Yalnızca anlamsal uyarı: "Son N adet", "Doğrulanmadı" (projeye özgü) |
| `--border` / `--input` / `--ring` | | | Ayraç, girdi kenarı, odak halkası |

Kurallar:
- Tek vurgu rengi marka yeşilidir. Kehribar süs değildir, yalnızca uyarı anlamı taşır.
- Renk tek başına anlam taşımaz: her durum etiketi metniyle okunur (AGENTS.md §12.4).
- Saf `#000` ve `#fff` kullanılmaz.

## 3. Ölçüler

| Ölçü | Değer |
|---|---|
| Köşe | `--radius: 0.5rem`. Girdi, düğme, iskelet `rounded-lg` (8px); kart ve panel `rounded-xl` (12px); etiket `rounded-full` |
| Dokunma hedefi | Düğme ve girdi mobilde `h-11` (44px), `lg` ve üstünde `h-10` (40px) |
| Kırılımlar | Tailwind varsayılanları; düzen mobil önce, `sm` (640) ızgarayı ikiye, `lg` (1024) üçe açar |
| Sayfa genişliği | Vitrin `max-w-6xl`; hesap ve durum ekranları `max-w-md` tek sütun |
| Başlık çubuğu | 64px, yapışkan, yarı saydam zemin; masaüstünde tek satır |

## 4. Bileşenler

### Temel bileşenler (`web/src/components/ui/`, shadcn/ui kökenli)

| Bileşen | Varsayılandan farkı |
|---|---|
| `Button`, `buttonVariants` | 44/40px yükseklik; `lg` ve `icon` boyutları buna göre; `buttonVariants()` `Link`'e verilir |
| `Input` | 44/40px; mobilde 16px yazı (iOS yakınlaştırmasını önler); `aria-invalid` kırmızı kenar + halka |
| `Label` | Değişmedi |
| `Field`, `FieldLabel`, `FieldDescription`, `FieldError`, `FieldGroup` | `FieldError` `role="alert"` taşımaz ve tek mesaj alır; istemci kancası yok, sunucuda render edilir |
| `Card` ve alt parçaları | İç boşluk mobilde 20px, masaüstünde 24px; başlık `font-semibold` |
| `Badge` | `warning` varyantı eklendi; yükseklik 24px |
| `Alert` | `success` varyantı eklendi; rol çağırandan (`alert` / `status`) |
| `Separator` | Değişmedi |
| `Skeleton` | `motion-reduce:animate-none`; köşe `rounded-lg` |

### Proje bileşenleri

| Bileşen | Yer | Görev |
|---|---|---|
| `AppShell` | `components/` | Başlık (marka işareti `Sprout`, slogan yalnızca masaüstünde, oturum alanı) ve alt bilgi |
| `SessionNav` | `components/` | Oturum durumuna göre "Giriş yap / Kayıt ol" veya maskeli e-posta + çıkış |
| `StatusPanel` | `components/` | Tam sayfa hata ve bulunamadı kutusu: ikon, başlık, tek cümle, tek eylem |
| `AccountScreen` | `features/account/components/` | Hesap ekranlarının ortak tek sütun iskeleti |
| `AccountSkeleton` | `features/account/components/` | Hesap sayfalarının yükleniyor iskeleti |
| `FormField` | `features/account/components/` | Etiket + girdi + yardım + hata; `aria-describedby` bağları |
| `FormMessage` | `features/account/components/` | Formun tek bildirim biçimi: hata (`alert`), başarı ve bilgi (`status`) |
| `ProductList` | `features/catalog/components/` | Ürün kartı ızgarası ve boş durum; stok `Badge` ile |

## 5. Dört durum

| Durum | Biçim |
|---|---|
| Yükleniyor | Son düzenin ölçüsünü taşıyan `Skeleton` blokları; dönen gösterge yok |
| Boş | Kesik çizgili kutu, ikon, başlık, ne bekleneceğini söyleyen tek cümle |
| Hata | `StatusPanel` (`role="alert"`) + "Yeniden dene"; form içinde `FormMessage tone="error"` |
| Yetkisiz | Korunan sayfa `layout.tsx` kapısında `/giris?next=…`'e yönlendirir; giriş sayfası nedenini söyler |

## 6. Bilinçli olarak olmayanlar

- **Ürün fotoğrafı** (KALANLAR B3): kart tipografiye ve fiyata yaslanır; yer tutucu veya stok görsel kullanılmaz.
- **Elle tema değiştirici:** tema işletim sistemini izler.
- **Hareket kütüphanesi:** hareket yalnızca CSS geçişi; `motion` paketi bütçede değil.

# Tasarım Sistemi

**Son doğrulama:** 2026-10-07 · Kararlar: [ADR-027](../adr/027-shadcn-ui-design-system.md), [ADR-028](../adr/028-theme-choice-and-color-roles.md) · Ekleme rehberi: [UI bileşeni ekleme](../how-to/ui-bileseni-ekleme.md)

> Bu sayfa `web/` arayüzünün **mevcut** tasarım sisteminin dökümüdür. Kaynak kod esastır: token'lar `web/src/app/globals.css`, bileşenler `web/src/components/ui/`. Kod değiştiğinde bu sayfa aynı commit'te güncellenir.

---

## 1. Yön

| | |
|---|---|
| **Okuma** | Üreticiden doğrudan alan sıradan alıcı ve sahada telefonla çalışan satıcı için güven odaklı pazaryeri |
| **Ayarlar** | Düzen çeşitliliği 4 · Hareket 3 · Yoğunluk 5 (sakin, öngörülebilir, telefonda rahat) |
| **Altyapı** | Tailwind CSS v4 + shadcn/ui `base-nova` (Base UI primitive'leri) + Lucide ikonları |
| **Yazı tipi** | Geist (gövde ve başlık), Geist Mono (giriş kodu); `next/font`, `latin` + `latin-ext` |
| **Tema** | Açık / Koyu / Sistem menüsü (başlıkta, tek ikon); varsayılan Sistem ([ADR-028](../adr/028-theme-choice-and-color-roles.md)) |
| **Hareket** | Yalnızca geri bildirim: düğmeye basınca 1px çökme, iskelette nabız (`motion-reduce` ile durur) |

## 2. Renkler ([ADR-028](../adr/028-theme-choice-and-color-roles.md))

İki katman. **Ham merdivenler** yalnızca `globals.css` içinde kullanılır; **anlamsal roller** Tailwind sınıfına açılır ve bileşenler yalnızca rolleri kullanır (`bg-primary`, `text-warning` …).

### 2.1 Merdivenler

| Merdiven | Adımlar | Not |
|---|---|---|
| `--green-*` (marka, ton 145–153) | 50, 100 … 900, 950 | 700 açık temada, 400 koyu temada birincil |
| `--neutral-*` (yeşile çalan gri) | 0, 50, 100 … 950 | Saf beyaz / siyah yok; 0 = kart, 950 = koyu zemin |
| `--amber-*`, `--red-*`, `--blue-*` | 50, 400, 600/700, 950 | Yalnızca durum rolleri için |

Adım görevleri Radix ölçeğini izler: 50–100 zemin, 200–300 kenar, 600–700 dolu zemin, 900–950 metin.

### 2.2 Roller

| Rol (+ `-foreground` eşi) | Açık | Koyu | Kullanım |
|---|---|---|---|
| `background` / `foreground` | neutral-50 / 950 | neutral-950 / 50 | Sayfa zemini ve ana metin |
| `card`, `popover` | neutral-0 | neutral-900 | Kart, form paneli, girdi zemini |
| `primary` | green-700 / 50 | green-400 / 950 | Tek vurgu: birincil düğme, bağlantı, marka işareti |
| `secondary` | green-100 / 900 | green-900 / 100 | Düşük vurgulu eylem, seçili seçenek zemini (ikinci bir renk değil) |
| `muted` | neutral-100 / 600 | neutral-800 / 400 | İskelet, ikincil metin |
| `accent` | neutral-100 | neutral-800 | Üzerine gelme zemini |
| `destructive` | red-600 / 50 | red-400 / 950 | Hata metni ve kutusu, "Stokta yok" |
| `success` | green-700 / 50 | green-400 / 950 | "Stokta", "Doğrulandı", başarı bildirimi |
| `warning` | amber-700 / 50 | amber-400 / 950 | "Son N adet", "Doğrulanmadı" |
| `info` | blue-600 / 50 | blue-400 / 950 | Bilgi bildirimi (giriş kodu gönderildi) |
| `border` / `input` / `ring` | neutral-200 / 300 / green-600 | %10 / %16 beyaz / green-500 | Ayraç, girdi kenarı, odak halkası |

### 2.3 Kontrast (WCAG AA, metin 4,5:1)

Tüm çiftler iki temada hesaplandı (OKLCH → sRGB göreli parlaklık; tonlu zeminler `color-mix(in oklab)` ile):

| Çift | Açık | Koyu |
|---|---|---|
| foreground / background | 18,14 | 18,14 |
| muted-foreground / card | 6,70 | 7,51 |
| primary-foreground / primary | 6,62 | 8,59 |
| secondary-foreground / secondary | 12,06 | 12,06 |
| destructive / card | 5,77 | 6,71 |
| warning / card | 6,17 | 10,07 |
| info / card | 5,88 | 7,85 |
| warning metni / warning tonlu zemin | 5,16 | 7,25 |
| destructive metni / destructive tonlu zemin | 5,01 | 5,02 |

Kurallar:
- Tek vurgu rengi marka yeşilidir. Durum renkleri süs değildir, yalnızca anlam taşır.
- Renk tek başına anlam taşımaz: her durum etiketi metniyle okunur (AGENTS.md §12.4).
- Yeni rol eklenirse `-foreground` eşiyle eklenir ve kontrastı iki temada hesaplanıp bu tabloya yazılır.

### 2.4 Tema seçimi

| Seçim | `<html data-theme>` | Davranış |
|---|---|---|
| Açık | `light` | `color-scheme: light`; işletim sistemi koyu olsa bile açık |
| Koyu | `dark` | `color-scheme: dark` |
| Sistem (varsayılan) | `system` | `color-scheme: light dark`; `prefers-color-scheme` izlenir |

Tercih `bahceden_theme` çerezinde (bir yıl). Kök düzen onu sunucuda okur; ilk boyama doğru temayla gelir. Seçici (`ThemeMenu`) başlıkta, oturum alanının yanında tek bir ikon düğmesidir. İkon seçili temayı gösterir, erişilebilir adı "Tema: Koyu" gibi seçimi söyler. Tıklanınca altında üç seçenekli bir menü açılır: seçili olanın yanında onay işareti var, ok tuşlarıyla gezilir, Esc ile kapanır.

## 3. Ölçüler

| Ölçü | Değer |
|---|---|
| Köşe | `--radius: 0.5rem`. Girdi, düğme, iskelet `rounded-lg` (8px); kart ve panel `rounded-xl` (12px); etiket `rounded-full` |
| Dokunma hedefi | Düğme ve girdi mobilde `h-11` (44px), `lg` ve üstünde `h-10` (40px) |
| Kırılımlar | Tailwind varsayılanları; düzen mobil önce, `sm` (640) ızgarayı ikiye, `lg` (1024) üçe açar |
| Sayfa genişliği | Vitrin `max-w-6xl`; hesap ve durum ekranları `max-w-md` tek sütun |
| Başlık çubuğu | 64px, yapışkan, yarı saydam zemin; her genişlikte tek satır. 375px'de oturumsuz başlık (marka + "Giriş yap" + "Kayıt ol" + tema ikonu) sığsın diye mobilde yan boşluk 12px, düğme iç boşluğu daraltılmış; başlığa öğe eklemeden önce bu genişlik kontrol edilir |

## 4. Bileşenler

### Temel bileşenler (`web/src/components/ui/`, shadcn/ui kökenli)

| Bileşen | Varsayılandan farkı |
|---|---|
| `Button`, `buttonVariants` | 44/40px yükseklik; `lg` ve `icon` boyutları buna göre; `buttonVariants()` `Link`'e verilir |
| `Input` | 44/40px; mobilde 16px yazı (iOS yakınlaştırmasını önler); `aria-invalid` kırmızı kenar + halka |
| `Label` | Değişmedi |
| `Field`, `FieldLabel`, `FieldDescription`, `FieldError`, `FieldGroup` | `FieldError` `role="alert"` taşımaz ve tek mesaj alır; istemci kancası yok, sunucuda render edilir |
| `Card` ve alt parçaları | İç boşluk mobilde 20px, masaüstünde 24px; başlık `font-semibold` |
| `Badge` | `success`, `warning`, `info` varyantları eklendi (durum rolleri); yükseklik 24px |
| `Alert` | `success` ve `info` varyantları eklendi: metin ön plan renginde, durumu ikon ve kenar taşır; rol çağırandan (`alert` / `status`) |
| `Separator` | Değişmedi |
| `DropdownMenu` ve parçaları (Base UI Menu) | Yalnızca kullanılan parçalar alındı (kök, tetikleyici, içerik, grup başlığı, radyo grubu/öğesi); öğe yüksekliği 44/36px; içerik tetikleyici genişliğine kilitlenmez (`min-w-40`), varsayılan hizalama sağ |
| `Skeleton` | `motion-reduce:animate-none`; köşe `rounded-lg` |

### Proje bileşenleri

| Bileşen | Yer | Görev |
|---|---|---|
| `AppShell` | `components/` | Başlık (marka işareti `Sprout`, slogan yalnızca masaüstünde, oturum alanı + tema menüsü) ve alt bilgi (marka) |
| `ThemeMenu` | `components/` | Tek ikonlu tetikleyici + Açık / Koyu / Sistem menüsü; seçimi `data-theme`'ya ve çereze yazar |
| `SessionNav` | `components/` | Oturum durumuna göre "Giriş yap / Kayıt ol" veya maskeli e-posta + çıkış |
| `StatusPanel` | `components/` | Tam sayfa hata ve bulunamadı kutusu: ikon (hatada `destructive` tonlu), başlık, tek cümle, tek eylem |
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
- **Hareket kütüphanesi:** hareket yalnızca CSS geçişi; `motion` paketi bütçede değil.

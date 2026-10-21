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
| **Dil** | Türkçe / English menüsü (başlıkta, tema menüsünün yanında, tek ikon); çerez, yoksa tarayıcı dili, yoksa Türkçe ([ADR-030](../adr/030-locale-from-cookie-then-header.md)). Metinler `web/messages/*.json` ([ADR-029](../adr/029-next-intl-without-routing.md)) |
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
| `primary` | green-700 / 50 | green-400 / 950 | Tek vurgu: birincil düğme, bağlantı |
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

### 2.4 Logo ve marka renkleri

Kaynak marka paketi `docs/assets/brand/` altındadır (SVG; yazı tipi Fredoka yola çevrilmiş, font yüklenmez):

| Dosya | Kullanım |
|---|---|
| ![logo](../assets/brand/bahceden-logo.svg){ width="200" } `bahceden-logo.svg` | Açık zeminde tam logo |
| `bahceden-logo-dark-bg.svg` | Koyu zeminde tam logo (kelime işareti krem) |
| `bahceden-logo-mono.svg`, `bahceden-mark-mono.svg` | Tek renk (`currentColor`) |
| `bahceden-icon.svg` | Karolu uygulama ikonu; sekme ikonu (`web/src/app/icon.svg`) bunun kopyası |
| `bahceden-mark.svg`, `bahceden-mark-dark-bg.svg` | Karosuz "b" |
| `og-image.svg` | 1200×630 paylaşım görseli; **bağlı değil**: mutlak site adresi (`metadataBase`) üretim alan adına bağlı (KALANLAR B5) |

Ham renkler yalnızca `globals.css`'teki `--brand-*` merdiveninde, bileşen yalnızca `logo-*` rollerini kullanır:

| Rol | Açık | Koyu |
|---|---|---|
| `logo-tile` (karo) | `#24302A` | `#24302A` |
| `logo-letter` ("b") | `#B5E04A` | `#B5E04A` |
| `logo-leaf` (yaprak) | `#7DB534` | `#7DB534` |
| `logo-wordmark` (kelime işareti) | `#24302A` | `#F6F2E6` |

Logo rolleri yalnızca logoda kullanılır; arayüz rengi değildir (tek vurgu `primary` kalır). Tarayıcı ve PWA ikonları Next.js dosya kuralıyla `web/src/app/` altında (`favicon.ico`, `icon.svg`, `apple-icon.png`, `manifest.webmanifest`), manifestin ikonları `web/public/icons/` altındadır; `theme-color` `#24302A`. Kurallar: logo 32px'ten kısa gösterilmez (daha dar alanda `BrandMark`); açık zemine karosuz lime "b" konmaz.

### 2.5 Tema seçimi

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
| Başlık çubuğu | 64px, yapışkan, yarı saydam zemin; her genişlikte tek satır. 375px'de oturumsuz başlık (logo + "Giriş yap" + "Kayıt ol" + dil ve tema ikonları) sığsın diye mobilde yan boşluk 12px, düğme iç boşluğu daraltılmış ve 420px altında logonun kelime işareti düşer, yalnızca "b" karosu kalır; başlığa öğe eklemeden önce bu genişlik kontrol edilir |

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
| `RadioGroup`, `RadioGroupItem` (Base UI Radio) | Dokunma alanı `after:` ile genişletilir; seçenek satırı kullanan ekranda etiketle sarılır (en az 44px). Seçili satır `has-[[data-checked]]` ile vurgulanır |
| `Textarea` | `Input` ile aynı ölçüler: mobilde 16px yazı, kart zemini; en az iki satır, içeriğe göre büyür |
| `Dialog` ve parçaları (Base UI Dialog) | Köşedeki kapatma ikonu ve hazır "Close" düğmesi alınmadı (sabit İngilizce metin); vazgeç düğmesini ekran koyar. Arka plan `foreground/30`; hareket `motion-reduce:` ile durur; alt bilgi mobilde alt alta |

### Proje bileşenleri

| Bileşen | Yer | Görev |
|---|---|---|
| `AppShell` | `components/` | Başlık (logo, slogan yalnızca masaüstünde, oturum alanı + tema menüsü) ve alt bilgi (marka) |
| `BrandLogo` / `BrandMark` | `components/` | Satır içi SVG logo ve dar alan için yalnızca karo; renkler `logo-*` rollerinden, erişilebilir adı çağıran verir |
| `LocaleMenu` | `components/` | Tek ikonlu tetikleyici + dil menüsü; seçenekler kendi dilinde, seçim çereze yazılır ve sayfa sunucuda yenilenir |
| `ThemeMenu` | `components/` | Tek ikonlu tetikleyici + Açık / Koyu / Sistem menüsü; seçimi `data-theme`'ya ve çereze yazar |
| `SessionNav` | `components/` | Oturum durumuna göre "Giriş yap / Kayıt ol" veya sepet (adet rozetiyle) + maskeli e-posta + çıkış; `ROLE_SELLER`'da "Ürünlerim", `ROLE_ADMIN`'de "Yönetim". Dar ekranda çıkış yalnızca ikon ([ADR-068](../adr/068-checkout-web-routes-server-cart-count.md)) |
| `Pagination` | `components/` | Sayfalı listelerin önceki / sonraki gezintisi; diğer sorgu parametrelerini korur. Vitrin, satıcı ilanları ve admin listeleri paylaşır |
| `ListSkeleton` | `components/` | Liste sayfalarının yükleniyor iskeleti (isteğe bağlı sekmelerle) |
| `ReasonDialog` | `components/` | Gerekçe isteyen yıkıcı eylem; satıcı reddi, satıcı askısı, ilan kaldırma ve sipariş iptali paylaşır ([ADR-053](../adr/053-admin-listing-moderation-shared-reason-dialog.md)). `reasonOptional` ile gerekçe boş bırakılabilir (alıcının iptali) |
| `SectionTabs` | `components/` | Panel bölümleri arası alt çizgili sekmeler (`aria-current`); `AdminNav` ve `SellerNav` kullanır. Dar ekranda yatay kayar, etkin bölüm görünür alana getirilir (`ScrollingNav`) |
| `FilterTabs` | `components/` | Liste sayfalarının `?status=` süzgeci (dolgulu düğmeler); admin satıcıları, admin ilanları ve gelen siparişler paylaşır. Dar ekranda yatay kayar, etkin seçenek görünür alana getirilir (`ScrollingNav`) |
| `ScrollingNav` | `components/` | Yatay kayan sekme şeridi (istemci): etkin seçenek (`aria-current="page"`) görünür alanın dışındaysa yalnızca şeridin `scrollLeft`'iyle ortalanır; sayfa dikeyde kaymaz. `SectionTabs` ve `FilterTabs` kullanır |
| `StatusPanel` | `components/` | Tam sayfa hata ve bulunamadı kutusu: ikon (hatada `destructive` tonlu), başlık, tek cümle, tek eylem |
| `AccountScreen` | `features/account/components/` | Hesap ekranlarının ortak tek sütun iskeleti |
| `AccountSkeleton` | `features/account/components/` | Hesap sayfalarının yükleniyor iskeleti |
| `FormField` | `features/account/components/` | Etiket + girdi + yardım + hata; `aria-describedby` bağları |
| `FormMessage` | `features/account/components/` | Formun tek bildirim biçimi: hata (`alert`), başarı ve bilgi (`status`) |
| `ProductList` | `features/catalog/components/` | Ürün kartı ızgarası ve boş durum; stok `Badge` ile |
| `SellerStatusBadge` | `features/seller/components/` | Satıcı durumu rozeti: `PENDING` `info`, `APPROVED` `success`, `REJECTED` `destructive`, `SUSPENDED` `warning`; durum her zaman metinle de yazılır |
| `SellerApplicationForm` | `features/seller/components/` | Başvuru ve yeniden gönderim formu; belge türü radyo grubu, dosya alanı, istemcide erken tür/boyut uyarısı |
| `SellerRecordSummary`, `SellerCard` | `features/seller/components/` | Satıcının kendi kaydı ve "Hesabım"daki satıcılık bölümü |
| `SellerDecisionActions` | `features/admin/components/` | Admin karar eylemleri: onay birincil ve büyük; ret ve askı yıkıcı, gerekçe isteyen diyalogla |
| `AdminNav`, `ProductModerationActions` | `features/admin/components/` | Admin bölümleri arası sekmeler; ilan kaldırma (diyalog) ve yeniden açma |
| `ListingForm`, `ListingStatusBadge` | `features/listings/components/` | İlan bilgileri formu (fiyat TL girilir, kuruşa çevrilir; birim radyo grubu) ve durum rozeti |
| `PhotoManager` | `features/listings/components/` | Fotoğraf yükleme, öne / arkaya düğmeleriyle sıralama (sürükle-bırak yok), onaylı silme, kapak etiketi |
| `ListingActions` | `features/listings/components/` | Yayın eylemleri: yayına al birincil; fotoğraf yoksa pasif ve nedeni yazılı |
| `ProductGallery` | `features/catalog/components/` | Ürün detayında büyük görsel ve küçük resim düğmeleri (`aria-current`) |
| `SellerNav` | `features/seller/components/` | Satıcı paneli sekmeleri: Ürünlerim · Gelen siparişler · Ödeme bilgileri |
| `PayoutForm`, `PayoutMissingNotice` | `features/payout/components/` | Ödeme bilgileri formu (tür radyo grubu, türe göre vergi dairesi; numaralar kayıttan sonra boşalır) ve ödeme hesabı etkin değilken ilan ekranlarındaki uyarı |
| `QuantityStepper` | `features/cart/components/` | Eksi · sayı · artı; 44 px düğmeler, sınırlar stok ve 99 |
| `AddToCart` | `features/cart/components/` | Ürün detayında adet ve "Sepete ekle"; oturumsuz alıcıya giriş bağlantısı (`SignInToBuy`) |
| `CartGroups`, `CartLineControls`, `CartTotals` | `features/cart/components/` | Satıcı grubu kartları (sepette düzenlenebilir, ödemede salt okunur), satır eylemleri (dar ekranda kendi satırında) ve toplamlar |
| `AddressForm`, `AddressBook` | `features/addresses/components/` | Adres formu (il seçenekleri açık radyo grubu) ve iki adımlı silmeli adres defteri |
| `CheckoutForm` | `features/orders/components/` | Teslimat adresi seçimi veya ekleme, ödemeye geçiş; tekrar anahtarı ekran boyunca korunur |
| `BuyerOrderView` | `features/orders/components/` | Alıcının sipariş detayı: satıcı siparişleri, son tarih, süre aşımı, adres, toplam |
| `OrderStatusBadge`, `SellerOrderStatusBadge` | `features/orders/components/` | Sipariş durumu (`PAID` `success`, bekleyen `warning`, başarısız `destructive`) ve satıcı siparişi durumu (`NEW` `info`, iptal `secondary`) |
| `CancelSellerOrder` | `features/orders/components/` | Kargodan önce iptal: alıcıda isteğe bağlı, satıcıda zorunlu gerekçeli onay penceresi |

## 5. Dört durum

| Durum | Biçim |
|---|---|
| Yükleniyor | Son düzenin ölçüsünü taşıyan `Skeleton` blokları; dönen gösterge yok |
| Boş | Kesik çizgili kutu, ikon, başlık, ne bekleneceğini söyleyen tek cümle |
| Hata | `StatusPanel` (`role="alert"`) + "Yeniden dene"; form içinde `FormMessage tone="error"` |
| Yetkisiz | Korunan sayfa `layout.tsx` kapısında `/login?next=…`'e yönlendirir; giriş sayfası nedenini söyler |

## 6. Bilinçli olarak olmayanlar

- **Fotoğrafsız ürün görseli:** kapak fotoğrafı varsa kartın üstünde durur. Fotoğrafı olmayan eski ürünlerde kart tipografiye ve fiyata yaslanır; yer tutucu veya stok görsel kullanılmaz. Görseller sunucuda boyutlandırılır, `next/image` `unoptimized` kullanılır ([ADR-051](../adr/051-photo-serving-public-key-registered-web-proxy.md)).
- **Dosya seçici metni:** dosya girdisinin içindeki "Dosya seç / Dosya seçilmedi" metni tarayıcının dilindedir, uygulamanın dilinde değil. Yerel denetim bilinçli olarak korunuyor; özel bir yükleme bileşeni ihtiyaç doğunca gelir.
- **Hareket kütüphanesi:** hareket yalnızca CSS geçişi; `motion` paketi bütçede değil.

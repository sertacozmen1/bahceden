# [ADR-028] Tema seçimi çerezle sunucuda; renkler iki katmanlı (merdiven + rol)

**Tarih:** 2026-10-07
**Durum:** Accepted · [ADR-027](027-shadcn-ui-design-system.md)'nin "elle tema değiştirici yok" maddesinin yerini alır

---

## Context

[ADR-027](027-shadcn-ui-design-system.md) temayı yalnızca işletim sistemi tercihine bırakmıştı. Proje sahibi ekranda üç seçenekli bir tema seçici istedi: Açık, Koyu, Sistem. Seçici başlıkta, hesap alanının yanında tek bir ikon olmalı ve seçenekler altında açılan bir menüde durmalı. Aynı istekte renklerin "sektör standardı" bir rol yapısıyla belirlenmesini de istedi.

İki teknik kısıt:

- **Renk sıçraması.** Tercih yalnızca tarayıcıda (`localStorage`) tutulursa sunucu temayı bilmez. Sayfa önce yanlış temada boyanır, betik sonradan düzeltir; ya da `<head>` içine engelleyici bir betik gerekir.
- **Bütçe.** Bu sorunu çözen yaygın paket `next-themes` bütçede değil (AGENTS.md K7).

Araştırmada renk tarafında yerleşik desen iki katmanlı çıktı:

- **Ham merdivenler:** 50–950 adımları. Adımların görevi Radix ölçeğinde tanımlıdır: açık adımlar zemin, orta adımlar kenar, koyu adımlar dolu zemin ve metin.
- **Anlamsal roller:** üstte durur ve her zemin rolünün kendi metin eşi vardır ("on-primary" / `primary-foreground`).
- **Durum rolleri:** `success`, `warning`, `danger`, `info` ayrı rollerdir; renk anlam taşır, süs değildir.

## Decision

1. **Tercih bir çerezdedir** (`bahceden_theme` = `light` | `dark` | `system`, bir yıl, `SameSite=Lax`).
   - Kök düzen çerezi sunucuda okur ve `<html data-theme>` olarak basar.
   - Tanınmayan değer `system`'e düşer.
   - Çerez kişisel veri taşımaz. Tarayıcı JavaScript'i yazabilsin diye `HttpOnly` değildir.
2. **Seçici** (`ThemeMenu`) başlıkta, oturum alanının hemen yanında tek bir ikon düğmesidir.
   - İkon seçili temayı gösterir; erişilebilir adı seçimi de söyler ("Tema: Koyu").
   - Tıklanınca altında Açık / Koyu / Sistem seçeneklerini içeren bir menü açılır. Menü shadcn/ui `dropdown-menu` bileşenidir ve Base UI Menu üzerine kuruludur; yeni paket gerekmez.
   - Seçim `data-theme`'yı anında değiştirir ve çereze yazar; sunucuya istek gitmez.
   - Tek ikon 375px başlığa, marka ve iki oturum düğmesinin yanına sığar. Üç seçeneği yan yana göstermek sığmıyordu.
3. **Roller `light-dark()` ile tek yerde iki değer taşır.** Hangi değerin seçileceğini `color-scheme` belirler:
   - `system` için `light dark`;
   - `light` ve `dark` için tek değer.
   - Tailwind'in `dark:` varyantı aynı kuralı izler (`[data-theme=dark]` ya da `system` + `prefers-color-scheme: dark`).
   - Derleyici (Lightning CSS) `light-dark()`'ı eski tarayıcılar için değişken çiftlerine çevirir.
4. **Renk iki katmanlıdır** (`web/src/app/globals.css`):
   - **Merdivenler** yalnızca o dosyada kullanılır ve Tailwind sınıfına açılmaz: `--green-50…950`, `--neutral-0…950`, durum renkleri (`amber`, `red`, `blue`).
   - **Roller:** shadcn sözleşmesi (`background`, `foreground`, `card`, `popover`, `primary`, `secondary`, `muted`, `accent`, `destructive`, `border`, `input`, `ring`) ve projeye özgü durum rolleri `success`, `warning`, `info`. Her dolu zemin rolünün `*-foreground` eşi vardır.
   - **`secondary`'nin anlamı shadcn'deki gibidir:** düşük vurgulu eylem ve yumuşak marka zemini. İkinci bir marka rengi değildir; tek vurgu marka yeşilidir.
5. **Her rol çifti iki temada WCAG AA'yı (4,5:1) karşılar.** Hesaplanan en düşük değer 5,01: tonlu zemin üzerindeki hata metni, koyu tema. Değerler [Tasarım sistemi](../reference/03-design-system.md) sayfasında.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `next-themes` | Bütçe dışı paket. Çerez + sunucu tarafı `data-theme` aynı işi sıçramasız ve paketsiz yapıyor. |
| `localStorage` + `<head>` içinde engelleyici betik | Sunucu temayı bilmez; her sayfaya satır içi betik gerekir, bu da ileride sıkı bir CSP kurmayı zorlaştırır. |
| `.dark` sınıfı + iki ayrı token bloğu | Koyu değerler iki kez yazılır (seçili "Koyu" ve "Sistem" + OS koyu); `light-dark()` her rolü tek satırda tutuyor. |
| Material 3 rol kümesi (`primary-container`, `tertiary`, `on-*` …) | shadcn bileşenleri shadcn adlarını kullanır; iki sözlük birlikte yaşarsa her bileşende eşleme gerekir. Material'den yalnızca ilke alındı: her zemin rolünün metin eşi var. |
| Üç seçenek yan yana (bölümlü denetim) | 375px başlığa sığmıyor; alt bilgiye konunca her sayfada kaydırma gerektiriyor. İlk sürüm buydu, proje sahibinin isteğiyle başlıktaki menüye dönüştü. |
| İkinci bir marka rengi (`secondary` = toprak / turuncu) | Tek vurgu kuralı (bilişsel yük) ve durum renkleriyle karışma riski; turuncu zaten uyarı anlamı taşıyor. |

## Consequences

### Olumlu
- Tema ilk boyamada doğru: sıçrama yok, betik yok, paket yok.
- Rol çiftleri ve kontrast tek dosyada; yeni bir bileşen rol kullanarak iki temada da okunur kalır.
- Durum rolleri (`success`, `warning`, `info`, `destructive`) etiket ve bildirimlerde tutarlı anlam taşıyor.

### Olumsuz
- Kök düzen çerez okuduğu için her sayfa dinamik render edilir. Bugün zaten öyle (oturum alanı); ileride statik sayfa gerekirse tema okuma yeniden düşünülmeli.
- Çerez tarayıcıda yazılabilir; değeri güvenilmez kabul edilir ve sunucuda doğrulanır (`parseTheme`).

### Nötr / Trade-off
- Seçenekler bir tıklama arkasında. Buna karşılık tema her sayfada aynı yerde ve başlık mobilde tek satır kalıyor.
- Başlıkta yer dar: 375px'de oturumsuz başlık sığsın diye mobilde oturum düğmelerinin iç boşluğu daraltıldı. Başlığa yeni öğe eklemek önce bu genişliği kontrol etmeyi gerektirir.

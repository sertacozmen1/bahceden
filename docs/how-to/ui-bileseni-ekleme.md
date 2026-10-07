# Nasıl Yapılır: Yeni bir UI bileşeni ekleme (shadcn/ui)

**Özet:** Bu rehber, `web/` arayüzüne shadcn/ui kayıt defterinden yeni bir temel bileşen (diyalog, açılır menü, seçim kutusu …) eklemeyi ve onu proje kurallarına uydurmayı anlatır. Karar ve gerekçe: [ADR-027](../adr/027-shadcn-ui-design-system.md). Token ve bileşen envanteri: [Tasarım sistemi](../reference/03-design-system.md).

## Ön koşullar

- [Yerel geliştirme ortamı](yerel-gelistirme.md) kurulu; `npm --prefix web ci` çalıştırılmış.
- `web/components.json` yerinde (stil `base-nova`, ikon seti `lucide`, `utils` takma adı `@/lib/utils`).
- Bileşen gerçekten paylaşılan bir sunum bileşeni. Tek ekranda kullanılacak bir parça `components/ui/` altına değil, ilgili `features/<domain>/components/` altına yazılır (AGENTS.md §12.2, K9).

---

## Adımlar

### 1. Bileşeni ekleyin

`web/` dizininde:

```bash
npx shadcn@4.21.3 add dialog
```

CLI dosyayı `src/components/ui/dialog.tsx` olarak yazar. Yeni bir npm paketi kurmaya çalışırsa **durun**: bütçedeki paketler (`@base-ui/react`, `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react`) dışındaki her paket K7 onayı ister.

### 2. Bağımlılık değişikliğini geri alın

CLI `cn` ve `shadcn` paketlerini ekler; ikisi de bilinçli olarak bütçede yok ([ADR-027](../adr/027-shadcn-ui-design-system.md) §9).

```bash
git diff web/package.json
git checkout web/package.json web/package-lock.json
npm --prefix web ci
```

### 3. İçe aktarmaları düzeltin

Kayıt defterindeki kod `cn`'i paketten alır. Proje yardımcısına çevirin:

```tsx
// Kayıt defterinden gelen
import { cn } from "cn";
// Projedeki
import { cn } from "@/lib/utils";
```

`@/registry/...` ile başlayan içe aktarmaları `@/components/ui/...` yapın.

### 4. Varsayılan halde bırakmayın

- **Dokunma hedefi:** etkileşimli öğe mobilde en az 44px (`h-11`), masaüstünde 40px (`lg:h-10`).
- **Renk:** yalnızca anlamsal token sınıfları (`bg-primary`, `text-muted-foreground`, `text-destructive`, `text-warning`). Sabit renk (`bg-green-700`, `#fff`) yazılmaz.
- **Köşe:** girdi ve düğme `rounded-lg`, kart ve panel `rounded-xl`.
- **Metin:** kullanıcıya görünen her metin `web/src/lib/texts.ts`'ten gelir.
- **Yorum:** dosyanın başına bileşenin ne olduğunu ve varsayılandan farkını söyleyen Türkçe JSDoc ekleyin.
- **Hareket:** animasyon varsa `motion-reduce:` ile durdurulur.
- **Özel varyant:** bileşen `globals.css`'te tanımlı olmayan bir `data-*` varyantı kullanıyorsa (örn. `data-checked`), shadcn'in `tailwind.css` dosyasındaki tanımı `globals.css`'e kopyalayın.

### 5. Doğrulayın

```bash
npm --prefix web run lint
npm --prefix web run typecheck
npm --prefix web test
npm --prefix web run build
```

Bileşeni kullanan ekran için davranış testi yazın (rol ve erişilebilir adla sorgu; AGENTS.md §12.6).

---

## Olası hatalar

- **Hata:** `Cannot find module 'cn'`.
  **Çözüm:** 3. adım atlanmış; içe aktarmayı `@/lib/utils` yapın.
- **Hata:** Bileşen stilsiz görünüyor, `data-open:` gibi sınıflar etkisiz.
  **Çözüm:** Özel varyant `globals.css`'te tanımlı değil; 4. adımdaki son maddeyi uygulayın.
- **Hata:** Testte `getByRole("alert")` birden fazla öğe buluyor.
  **Çözüm:** Alan seviyesindeki hata metnine `role="alert"` verilmez; yalnızca formun genel hatası uyarıdır (`FieldError`, ADR-027).
- **Hata:** `npm run build` yazı tipini indiremiyor.
  **Çözüm:** `next/font` derleme sırasında Google Fonts'a erişir; derleme ortamının ağ erişimini kontrol edin.

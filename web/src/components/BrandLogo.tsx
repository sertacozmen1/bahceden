import { cn } from "@/lib/utils";
import { WORDMARK_OFFSET, WORDMARK_PATH } from "@/components/wordmarkPath";

/**
 * Bahçeden logosu: koyu yeşil karo içinde yapraklı "b" + "bahçeden" kelime işareti.
 *
 * Kaynak marka paketidir (`docs/assets/brand/`); `<img>` yerine satır içi SVG
 * kullanılır, çünkü kelime işaretinin rengi temaya göre değişir (açıkta koyu
 * yeşil, koyuda krem; ADR-028) ve "Sistem" teması da `<img>` değiştirmeden
 * izlenebilsin. Renkler yalnızca `logo-*` rollerinden gelir (AGENTS.md §12.4).
 *
 * Logo süsleme değil, ad taşır: erişilebilir adı çağıran verir (`label`).
 * 32px'ten küçük alanda kelime işareti okunmaz; orada {@link BrandMark} kullanılır.
 */
export function BrandLogo({ label, className }: { readonly label: string; readonly className?: string }) {
  return (
    <svg viewBox="0 0 669.38 132.35" role="img" aria-label={label} className={cn("h-8 w-auto", className)}>
      <g transform="translate(8 8) scale(0.50834)">
        <MarkShapes />
      </g>
      <path
        transform={`translate(${WORDMARK_OFFSET.x} ${WORDMARK_OFFSET.y})`}
        className="fill-logo-wordmark"
        d={WORDMARK_PATH}
      />
    </svg>
  );
}

/** Yalnızca karo + "b": dar alanda (375px başlık) ve uygulama ikonu yerine. */
export function BrandMark({ label, className }: { readonly label: string; readonly className?: string }) {
  return (
    <svg viewBox="0 0 200 200" role="img" aria-label={label} className={cn("size-8", className)}>
      <MarkShapes />
    </svg>
  );
}

function MarkShapes() {
  return (
    <>
      <rect width="200" height="200" rx="44" className="fill-logo-tile" />
      <rect x="43" y="43" width="28" height="134" rx="14" className="fill-logo-letter" />
      <circle cx="107" cy="127" r="36" fill="none" strokeWidth="28" className="stroke-logo-letter" />
      <path d="M57 48 C64 24 92 16 112 24 C104 46 78 54 57 48 Z" className="fill-logo-leaf" />
    </>
  );
}

"use client";

import { type ReactNode, useEffect, useRef } from "react";

/**
 * Yatay kayan sekme şeridi: dar ekranda etkin seçenek (`aria-current="page"`)
 * görünür alanın dışındaysa şerit onu ortalayacak kadar kaydırılır.
 *
 * `FilterTabs` ve `SectionTabs` bunu paylaşır (K9). İstemci bileşenidir, çünkü
 * kaydırma ölçü ister ve ölçü yalnızca tarayıcıda vardır. Yalnızca şeridin
 * `scrollLeft`'i değişir; `scrollIntoView` kullanılmaz, çünkü o sayfayı da
 * dikeyde kaydırabilir. Etkin seçenek zaten görünüyorsa hiçbir şey yapılmaz.
 */
type ScrollingNavProps = {
  readonly label: string;
  readonly className: string;
  /** Etkin seçeneğin anahtarı: değişince (yeni sekme) yeniden ortalanır. */
  readonly currentKey: string;
  readonly children: ReactNode;
};

export function ScrollingNav({ label, className, currentKey, children }: ScrollingNavProps) {
  const ref = useRef<HTMLElement>(null);

  useEffect(() => {
    const nav = ref.current;
    const current = nav?.querySelector<HTMLElement>('[aria-current="page"]');
    if (!nav || !current) {
      return;
    }
    const strip = nav.getBoundingClientRect();
    const item = current.getBoundingClientRect();
    if (item.left >= strip.left && item.right <= strip.right) {
      return;
    }
    nav.scrollLeft += item.left - strip.left - (strip.width - item.width) / 2;
  }, [currentKey]);

  return (
    <nav ref={ref} aria-label={label} className={className}>
      {children}
    </nav>
  );
}

import type { Route } from "next";
import Link from "next/link";
import { ScrollingNav } from "@/components/ScrollingNav";
import { cn } from "@/lib/utils";

/**
 * Liste sayfalarının durum süzgeci: seçenekler adreste (`?status=`) durur,
 * böylece geri tuşu ve yenileme aynı listeyi gösterir. Admin satıcıları, admin
 * ilanları ve satıcının siparişleri paylaşır (K9). Etkin seçenek
 * `aria-current` taşır; dokunma hedefi 44px. Dar ekranda şerit yatay kayar ve
 * etkin seçenek görünür alana getirilir (`ScrollingNav`).
 */
type FilterTabsProps<T extends string> = {
  readonly label: string;
  readonly pathname: Route;
  readonly current: T;
  readonly options: ReadonlyArray<{ readonly value: T; readonly label: string }>;
};

export function FilterTabs<T extends string>({ label, pathname, current, options }: FilterTabsProps<T>) {
  return (
    <ScrollingNav label={label} className="mb-5 -mx-4 overflow-x-auto px-4" currentKey={current}>
      <ul className="flex gap-2">
        {options.map((option) => (
          <li key={option.value}>
            <Link
              href={{ pathname, query: { status: option.value } }}
              aria-current={option.value === current ? "page" : undefined}
              className={cn(
                "inline-flex h-11 items-center rounded-lg border px-4 text-sm font-medium whitespace-nowrap lg:h-10",
                option.value === current
                  ? "border-primary bg-primary text-primary-foreground"
                  : "border-border bg-card hover:bg-muted",
              )}
            >
              {option.label}
            </Link>
          </li>
        ))}
      </ul>
    </ScrollingNav>
  );
}

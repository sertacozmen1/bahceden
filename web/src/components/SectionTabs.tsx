import type { Route } from "next";
import Link from "next/link";
import { cn } from "@/lib/utils";

/**
 * Bir paneldeki bölümler arasında sekme gezintisi. Admin ve satıcı panelleri
 * aynı biçimi kullanır (K9); etkin bölüm `aria-current` taşır. Dokunma hedefi
 * 44px (AGENTS.md §12.4); dar ekranda sekmeler yatay kayar.
 */
type SectionTabsProps = {
  readonly label: string;
  readonly sections: ReadonlyArray<{ readonly href: Route; readonly label: string; readonly current: boolean }>;
};

export function SectionTabs({ label, sections }: SectionTabsProps) {
  return (
    <nav aria-label={label} className="mb-6 overflow-x-auto border-b border-border">
      <ul className="-mb-px flex gap-4">
        {sections.map((section) => (
          <li key={section.href} className="shrink-0">
            <Link
              href={section.href}
              aria-current={section.current ? "page" : undefined}
              className={cn(
                "inline-flex h-11 items-center border-b-2 text-sm font-medium",
                section.current
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted-foreground hover:text-foreground",
              )}
            >
              {section.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}

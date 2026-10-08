import type { Route } from "next";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";

/**
 * Admin bölümleri arasında gezinti: satıcı başvuruları ve ilanlar
 * (add-product-listings D7). Başlıktaki "Yönetim" bağlantısı ilk bölüme gider;
 * bu sekmeler ikisi arasında geçişi sağlar. Etkin bölüm `aria-current` taşır.
 */
const sections = [
  { key: "sellers", href: "/admin/sellers" },
  { key: "products", href: "/admin/products" },
] as const satisfies ReadonlyArray<{ key: string; href: Route }>;

export function AdminNav({ current }: { readonly current: (typeof sections)[number]["key"] }) {
  const t = useTranslations("admin.tabs");
  return (
    <nav aria-label={t("label")} className="mb-6 border-b border-border">
      <ul className="-mb-px flex gap-4">
        {sections.map((section) => (
          <li key={section.key}>
            <Link
              href={section.href}
              aria-current={section.key === current ? "page" : undefined}
              className={cn(
                "inline-flex h-11 items-center border-b-2 text-sm font-medium",
                section.key === current
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted-foreground hover:text-foreground",
              )}
            >
              {t(section.key)}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}

import type { Route } from "next";
import { useTranslations } from "next-intl";
import { SectionTabs } from "@/components/SectionTabs";

/**
 * Admin bölümleri arasında gezinti: satıcı başvuruları ve ilanlar
 * (add-product-listings D7). Başlıktaki "Yönetim" bağlantısı ilk bölüme gider;
 * bu sekmeler ikisi arasında geçişi sağlar.
 */
const sections = [
  { key: "sellers", href: "/admin/sellers" },
  { key: "products", href: "/admin/products" },
] as const satisfies ReadonlyArray<{ key: string; href: Route }>;

export function AdminNav({ current }: { readonly current: (typeof sections)[number]["key"] }) {
  const t = useTranslations("admin.tabs");
  return (
    <SectionTabs
      label={t("label")}
      sections={sections.map((section) => ({
        href: section.href,
        label: t(section.key),
        current: section.key === current,
      }))}
    />
  );
}

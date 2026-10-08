import type { Route } from "next";
import { useTranslations } from "next-intl";
import { SectionTabs } from "@/components/SectionTabs";

/**
 * Satıcı paneli bölümleri: ilanlar, gelen siparişler ve ödeme bilgileri (add-cart-and-checkout
 * D10). Başlıktaki "Ürünlerim" bağlantısı ilk bölüme gider; başlık dar ekranda
 * kalabalıklaşmasın diye diğer bölümler buradadır.
 */
const sections = [
  { key: "products", href: "/seller/products" },
  { key: "orders", href: "/seller/orders" },
  { key: "payout", href: "/seller/payout" },
] as const satisfies ReadonlyArray<{ key: string; href: Route }>;

export function SellerNav({ current }: { readonly current: (typeof sections)[number]["key"] }) {
  const t = useTranslations("seller.tabs");
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

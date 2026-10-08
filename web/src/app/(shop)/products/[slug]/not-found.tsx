import Link from "next/link";
import { SearchX } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";

/** Vitrinde görünmeyen veya olmayan ürün (storefront-catalog "Ürün detayı", AGENTS.md §12.5). */
export default async function ProductNotFound() {
  const t = await getTranslations();
  return (
    <StatusPanel icon={SearchX} title={t("storefront.detail.notFoundTitle")} body={t("storefront.detail.notFoundBody")}>
      <Link className={buttonVariants()} href="/">
        {t("storefront.detail.back")}
      </Link>
    </StatusPanel>
  );
}

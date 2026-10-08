import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Satıcının ilan ekranlarının yükleniyor iskeleti (AGENTS.md §12.5). */
export default function SellerProductsLoading() {
  const t = useTranslations("listings.list");
  return <ListSkeleton label={t("loading")} />;
}

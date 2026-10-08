import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Gelen siparişlerin yükleniyor iskeleti (AGENTS.md §12.5). */
export default function SellerOrdersLoading() {
  const t = useTranslations("sellerOrders");
  return <ListSkeleton label={t("loading")} withTabs />;
}

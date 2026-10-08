import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Siparişlerin yükleniyor iskeleti (AGENTS.md §12.5). */
export default function MyOrdersLoading() {
  const t = useTranslations("orders");
  return <ListSkeleton label={t("loading")} />;
}

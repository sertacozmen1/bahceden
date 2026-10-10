import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Admin sipariş ekranının yükleniyor iskeleti (AGENTS.md §12.5). */
export default function AdminOrdersLoading() {
  const t = useTranslations("admin.orders");
  return <ListSkeleton label={t("loading")} withTabs />;
}

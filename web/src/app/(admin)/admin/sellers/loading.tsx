import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Admin satıcı ekranlarının yükleniyor iskeleti (AGENTS.md §12.5). */
export default function AdminSellersLoading() {
  const t = useTranslations("admin.sellers");
  return <ListSkeleton label={t("loading")} withTabs />;
}

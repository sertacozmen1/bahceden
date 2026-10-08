import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Admin ilan ekranının yükleniyor iskeleti (AGENTS.md §12.5). */
export default function AdminProductsLoading() {
  const t = useTranslations("admin.products");
  return <ListSkeleton label={t("loading")} withTabs />;
}

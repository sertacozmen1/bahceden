import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Admin talep ekranının yükleniyor iskeleti (AGENTS.md §12.5). */
export default function AdminClaimsLoading() {
  const t = useTranslations("admin.claims");
  return <ListSkeleton label={t("loading")} withTabs />;
}

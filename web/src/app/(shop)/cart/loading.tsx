import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Sepetin yükleniyor iskeleti (AGENTS.md §12.5). */
export default function CartLoading() {
  const t = useTranslations("cart");
  return <ListSkeleton label={t("loading")} />;
}

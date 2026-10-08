import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Ödeme adımlarının yükleniyor iskeleti (AGENTS.md §12.5). */
export default function CheckoutLoading() {
  const t = useTranslations("checkout");
  return <ListSkeleton label={t("loading")} />;
}

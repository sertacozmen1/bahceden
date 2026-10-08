import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Ödeme bilgileri ekranının yükleniyor iskeleti (AGENTS.md §12.5). */
export default function SellerPayoutLoading() {
  const t = useTranslations("payout");
  return <ListSkeleton label={t("loading")} withTabs />;
}

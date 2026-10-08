import { useTranslations } from "next-intl";
import { ListSkeleton } from "@/components/ListSkeleton";

/** Adres defterinin yükleniyor iskeleti (AGENTS.md §12.5). */
export default function AddressesLoading() {
  const t = useTranslations("addresses");
  return <ListSkeleton label={t("loading")} />;
}

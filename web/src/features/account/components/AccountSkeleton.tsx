import { useTranslations } from "next-intl";
import { Skeleton } from "@/components/ui/skeleton";
import { AccountScreen } from "./AccountScreen";

/**
 * Hesap sayfalarının yükleniyor iskeleti (AGENTS.md §12.5). Aynı
 * `AccountScreen` ölçüsünü kullanır: içerik geldiğinde düzen sıçramaz.
 */
export function AccountSkeleton() {
  const t = useTranslations();
  return (
    <AccountScreen aria-busy="true" aria-label={t("account.myAccount.loading")}>
      <Skeleton className="h-6 w-2/5" />
      <Skeleton className="h-4 w-4/5" />
      <Skeleton className="h-28 w-full" />
      <Skeleton className="h-11 w-full lg:h-10" />
    </AccountScreen>
  );
}

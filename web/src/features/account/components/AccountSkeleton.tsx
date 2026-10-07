import { Skeleton } from "@/components/ui/skeleton";
import { texts } from "@/lib/texts";
import { AccountScreen } from "./AccountScreen";

/**
 * Hesap sayfalarının yükleniyor iskeleti (AGENTS.md §12.5). Aynı
 * `AccountScreen` ölçüsünü kullanır: içerik geldiğinde düzen sıçramaz.
 */
export function AccountSkeleton() {
  return (
    <AccountScreen aria-busy="true" aria-label={texts.account.myAccount.loading}>
      <Skeleton className="h-6 w-2/5" />
      <Skeleton className="h-4 w-4/5" />
      <Skeleton className="h-28 w-full" />
      <Skeleton className="h-11 w-full lg:h-10" />
    </AccountScreen>
  );
}

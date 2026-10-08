import { useTranslations } from "next-intl";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Admin satıcı ekranlarının yükleniyor iskeleti (AGENTS.md §12.5): başlık,
 * sekmeler ve birkaç satır; içerik geldiğinde düzen sıçramaz.
 */
const ROWS = 4;

export default function AdminSellersLoading() {
  const t = useTranslations("admin.sellers");
  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12" aria-busy="true" aria-label={t("loading")}>
      <Skeleton className="h-8 w-64" />
      <Skeleton className="mt-3 h-5 w-full max-w-md" />
      <div className="mt-6 flex gap-2">
        {Array.from({ length: ROWS }, (_, index) => (
          <Skeleton key={index} className="h-11 w-24 rounded-lg" />
        ))}
      </div>
      <div className="mt-5 flex flex-col gap-3">
        {Array.from({ length: ROWS }, (_, index) => (
          <Skeleton key={index} className="h-20 w-full rounded-xl" />
        ))}
      </div>
    </main>
  );
}

import { useTranslations } from "next-intl";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Yükleniyor durumu: içeriğin yerini tutan iskelet (AGENTS.md §12.5).
 *
 * Boş beyaz sayfa yasak; iskelet vitrinin ölçülerini ve ızgarasını birebir
 * kopyalar, bu yüzden içerik geldiğinde düzen sıçramaz.
 */
const SKELETON_CARD_COUNT = 6;

export default function Loading() {
  const t = useTranslations();
  return (
    <main
      className="mx-auto w-full max-w-6xl px-4 pt-8 pb-12 lg:px-6 lg:pt-12"
      aria-busy="true"
      aria-label={t("storefront.loading")}
    >
      <div className="mb-6 flex flex-col gap-3 lg:mb-8">
        <Skeleton className="h-9 w-40 lg:h-10" />
        <Skeleton className="h-5 w-80 max-w-full" />
        <Skeleton className="h-4 w-16" />
      </div>
      <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 lg:gap-5">
        {Array.from({ length: SKELETON_CARD_COUNT }, (_, index) => (
          <li key={index}>
            <Skeleton className="h-40 rounded-xl" />
          </li>
        ))}
      </ul>
    </main>
  );
}

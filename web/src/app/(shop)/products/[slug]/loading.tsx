import { useTranslations } from "next-intl";
import { Skeleton } from "@/components/ui/skeleton";

/** Ürün detayının yükleniyor iskeleti (AGENTS.md §12.5): galeri ve bilgi sütunu ölçüsünde. */
export default function ProductLoading() {
  const t = useTranslations("storefront.detail");
  return (
    <main className="mx-auto w-full max-w-5xl px-4 pt-6 pb-12 lg:px-6 lg:pt-10" aria-busy="true" aria-label={t("loading")}>
      <Skeleton className="mb-4 h-10 w-36" />
      <div className="grid gap-6 lg:grid-cols-2 lg:gap-10">
        <Skeleton className="aspect-[4/3] w-full rounded-xl" />
        <div className="flex flex-col gap-4">
          <Skeleton className="h-10 w-3/4" />
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="h-9 w-40" />
          <Skeleton className="h-24 w-full" />
        </div>
      </div>
    </main>
  );
}

import { Skeleton } from "@/components/ui/skeleton";

/**
 * Liste sayfalarının yükleniyor iskeleti (AGENTS.md §12.5): başlık, isteğe
 * bağlı sekmeler ve birkaç satır; içerik geldiğinde düzen sıçramaz. Admin
 * satıcı listesi ve satıcının ilan listesi paylaşır (K9).
 */
const ROWS = 4;

export function ListSkeleton({ label, withTabs = false }: { readonly label: string; readonly withTabs?: boolean }) {
  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12" aria-busy="true" aria-label={label}>
      <Skeleton className="h-8 w-64" />
      <Skeleton className="mt-3 h-5 w-full max-w-md" />
      {withTabs && (
        <div className="mt-6 flex gap-2">
          {Array.from({ length: ROWS }, (_, index) => (
            <Skeleton key={index} className="h-11 w-24 rounded-lg" />
          ))}
        </div>
      )}
      <div className="mt-5 flex flex-col gap-3">
        {Array.from({ length: ROWS }, (_, index) => (
          <Skeleton key={index} className="h-20 w-full rounded-xl" />
        ))}
      </div>
    </main>
  );
}

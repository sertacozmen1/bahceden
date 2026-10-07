import type { Metadata } from "next";
import Link from "next/link";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { fetchPublishedProducts } from "@/features/catalog/api";
import { ProductList } from "@/features/catalog/components/ProductList";
import { cn } from "@/lib/utils";

/**
 * Vitrin: yayınlanmış ürünlerin listesi (storefront-catalog spec'i).
 *
 * Server Component: veri sunucu tarafında çekilir, böylece sayfa arama
 * motorunda bulunabilir ve istemciye API adresi sızmaz (ADR-001, AGENTS.md §12.2).
 *
 * Hata durumu bilinçli olarak burada yakalanmaz: veri çekilemezse istisna
 * yukarı çıkar ve `error.tsx` devreye girer (§12.5).
 */

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("storefront.title"), description: t("storefront.subtitle") };
}

const DEFAULT_PAGE_SIZE = 20;

type SearchParams = { readonly page?: string };

export default async function StorefrontPage({
  searchParams,
}: {
  readonly searchParams: Promise<SearchParams>;
}) {
  const { page } = await searchParams;
  const t = await getTranslations();
  const currentPage = parsePage(page);
  const result = await fetchPublishedProducts(currentPage, DEFAULT_PAGE_SIZE);

  return (
    <main className="mx-auto w-full max-w-6xl px-4 pt-8 pb-12 lg:px-6 lg:pt-12">
      <header className="mb-6 flex flex-col gap-2 lg:mb-8">
        <h1 className="text-3xl font-semibold tracking-tight lg:text-4xl">{t("storefront.title")}</h1>
        <p className="max-w-prose text-pretty text-muted-foreground">{t("storefront.subtitle")}</p>
        <p className="text-sm font-medium text-muted-foreground tabular-nums">
          {t("storefront.resultCount", { count: result.totalElements })}
        </p>
      </header>

      <ProductList products={result.items} />

      {result.totalPages > 1 && (
        <nav
          className="mt-8 flex items-center justify-between gap-3"
          aria-label={t("pagination.current", { page: result.page + 1, totalPages: result.totalPages })}
        >
          {result.page > 0 ? (
            <Link
              className={buttonVariants({ variant: "outline" })}
              href={{ pathname: "/", query: { page: result.page - 1 } }}
            >
              <ChevronLeft aria-hidden />
              {t("pagination.previous")}
            </Link>
          ) : (
            <span className={cn(buttonVariants({ variant: "outline" }), "opacity-50")} aria-disabled="true">
              <ChevronLeft aria-hidden />
              {t("pagination.previous")}
            </span>
          )}

          <span className="text-sm text-muted-foreground tabular-nums">
            {t("pagination.current", { page: result.page + 1, totalPages: result.totalPages })}
          </span>

          {result.page + 1 < result.totalPages ? (
            <Link
              className={buttonVariants({ variant: "outline" })}
              href={{ pathname: "/", query: { page: result.page + 1 } }}
            >
              {t("pagination.next")}
              <ChevronRight aria-hidden />
            </Link>
          ) : (
            <span className={cn(buttonVariants({ variant: "outline" }), "opacity-50")} aria-disabled="true">
              {t("pagination.next")}
              <ChevronRight aria-hidden />
            </span>
          )}
        </nav>
      )}
    </main>
  );
}

/**
 * Sayfa numarası istemciden gelir: geçersiz veya negatif değer ilk sayfaya
 * düşer. Otorite sunucudadır, ama kullanıcıya gereksiz hata göstermeyiz.
 */
function parsePage(value: string | undefined): number {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}

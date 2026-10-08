import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Pagination, parsePage } from "@/components/Pagination";
import { fetchPublishedProducts } from "@/features/catalog/api";
import { ProductList } from "@/features/catalog/components/ProductList";

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

      <Pagination page={result.page} totalPages={result.totalPages} pathname="/" />
    </main>
  );
}

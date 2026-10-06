import type { Metadata } from "next";
import Link from "next/link";
import { fetchPublishedProducts } from "@/features/catalog/api";
import { ProductList } from "@/features/catalog/components/ProductList";
import { texts } from "@/lib/texts";
import styles from "./page.module.css";

/**
 * Vitrin: yayınlanmış ürünlerin listesi (storefront-catalog spec'i).
 *
 * Server Component: veri sunucu tarafında çekilir, böylece sayfa arama
 * motorunda bulunabilir ve istemciye API adresi sızmaz (ADR-001, AGENTS.md §12.2).
 *
 * Hata durumu bilinçli olarak burada yakalanmaz: veri çekilemezse istisna
 * yukarı çıkar ve `error.tsx` devreye girer (§12.5).
 */

export const metadata: Metadata = {
  title: texts.storefront.title,
  description: texts.storefront.subtitle,
};

const DEFAULT_PAGE_SIZE = 20;

type SearchParams = { readonly page?: string };

export default async function StorefrontPage({
  searchParams,
}: {
  readonly searchParams: Promise<SearchParams>;
}) {
  const { page } = await searchParams;
  const currentPage = parsePage(page);
  const result = await fetchPublishedProducts(currentPage, DEFAULT_PAGE_SIZE);

  return (
    <main className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>{texts.storefront.title}</h1>
        <p className={styles.subtitle}>{texts.storefront.subtitle}</p>
        <p className={styles.count}>{texts.storefront.resultCount(result.totalElements)}</p>
      </header>

      <ProductList products={result.items} />

      {result.totalPages > 1 && (
        <nav className={styles.pager} aria-label={texts.pagination.current(result.page + 1, result.totalPages)}>
          {result.page > 0 ? (
            <Link className={styles.pagerLink} href={{ pathname: "/", query: { page: result.page - 1 } }}>
              {texts.pagination.previous}
            </Link>
          ) : (
            <span className={styles.pagerDisabled}>{texts.pagination.previous}</span>
          )}

          <span className={styles.pagerStatus}>
            {texts.pagination.current(result.page + 1, result.totalPages)}
          </span>

          {result.page + 1 < result.totalPages ? (
            <Link className={styles.pagerLink} href={{ pathname: "/", query: { page: result.page + 1 } }}>
              {texts.pagination.next}
            </Link>
          ) : (
            <span className={styles.pagerDisabled}>{texts.pagination.next}</span>
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

import type { Metadata } from "next";
import Link from "next/link";
import { PackagePlus, Sprout } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { Pagination, parsePage } from "@/components/Pagination";
import { buttonVariants } from "@/components/ui/button";
import { requireRole } from "@/features/account/requireSession";
import { listOwnListings, type SellerListingPage } from "@/features/listings/api";
import { ListingStatusBadge } from "@/features/listings/components/ListingStatusBadge";
import { isSellerNotApproved, SellerNotApproved } from "@/features/listings/components/SellerNotApproved";
import { acceptsPayments, getOwnPayoutAccount } from "@/features/payout/api";
import { PayoutMissingNotice } from "@/features/payout/components/PayoutMissingNotice";
import { SellerNav } from "@/features/seller/components/SellerNav";
import { formatMoney } from "@/lib/money";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("listings.list.title") };
}

const PAGE_SIZE = 20;

/**
 * Satıcının ilanları (product-listings spec'i). En son güncellenen üstte;
 * birincil eylem "Yeni ilan". Onaylı olmayan satıcı hata yerine başvuru
 * yönlendirmesi görür (ADR-044). Ödeme hesabı etkin değilse ilanların satın
 * alınamayacağı söylenir (add-cart-and-checkout D10).
 */
export default async function SellerProductsPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly page?: string }>;
}) {
  const { page } = await searchParams;
  const t = await getTranslations();
  const locale = await getLocale();
  const { accessToken } = await requireRole("/seller/products", "ROLE_SELLER");

  let result: SellerListingPage;
  let paymentsReady: boolean;
  try {
    const [listings, payoutAccount] = await Promise.all([
      listOwnListings(accessToken, parsePage(page), PAGE_SIZE),
      getOwnPayoutAccount(accessToken),
    ]);
    result = listings;
    paymentsReady = acceptsPayments(payoutAccount);
  } catch (error) {
    if (isSellerNotApproved(error)) {
      return <SellerNotApproved />;
    }
    throw error;
  }

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <header className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t("listings.list.title")}</h1>
          <p className="max-w-prose text-pretty text-muted-foreground">{t("listings.list.intro")}</p>
        </div>
        <Link className={buttonVariants({ size: "lg", className: "shrink-0" })} href="/seller/products/new">
          <PackagePlus aria-hidden />
          {t("listings.list.new")}
        </Link>
      </header>
      <SellerNav current="products" />
      {!paymentsReady && <PayoutMissingNotice />}

      {result.items.length === 0 ? (
        <section
          aria-labelledby="listings-empty-title"
          className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center"
        >
          <span className="grid size-12 place-items-center rounded-full bg-secondary text-primary">
            <Sprout aria-hidden className="size-6" />
          </span>
          <h2 id="listings-empty-title" className="text-lg font-semibold tracking-tight">
            {t("listings.list.emptyTitle")}
          </h2>
          <p className="max-w-sm text-pretty text-muted-foreground">{t("listings.list.emptyBody")}</p>
        </section>
      ) : (
        <ul className="flex flex-col gap-3">
          {result.items.map((listing) => (
            <li key={listing.id}>
              <Link
                href={`/seller/products/${listing.id}`}
                className="flex flex-col gap-2 rounded-xl border border-border bg-card p-4 hover:bg-muted sm:flex-row sm:items-center sm:justify-between"
              >
                <span className="flex flex-col gap-1">
                  <span className="font-medium break-words">{listing.name}</span>
                  <span className="text-sm text-muted-foreground tabular-nums">
                    {formatMoney(listing.unitPrice, locale)} {t("storefront.perUnit", { unit: listing.unit })} ·{" "}
                    {t("listings.list.stock", { count: listing.stockQuantity })}
                  </span>
                </span>
                <ListingStatusBadge status={listing.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}

      <Pagination page={result.page} totalPages={result.totalPages} pathname="/seller/products" />
    </main>
  );
}

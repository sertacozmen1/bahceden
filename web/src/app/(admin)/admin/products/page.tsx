import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import { ExternalLink, Inbox } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { FilterTabs } from "@/components/FilterTabs";
import { Pagination, parsePage } from "@/components/Pagination";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { requireRole } from "@/features/account/requireSession";
import { adminProductStatuses, listProductsForAdmin } from "@/features/admin/api";
import { AdminNav } from "@/features/admin/components/AdminNav";
import { ProductModerationActions } from "@/features/admin/components/ProductModerationActions";
import { ListingStatusBadge } from "@/features/listings/components/ListingStatusBadge";
import { cardSize, photoUrl } from "@/lib/productPhotos";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("admin.products.title") };
}

const PAGE_SIZE = 20;

/**
 * Admin: ilan denetimi (product-listings spec'i "Admin ilanı kaldırır ve yeniden
 * açar", D7). Admin ilanı alıcının gördüğü gibi vitrin sayfasında inceler;
 * satırda kapak, ad, üretici, durum ve tek eylem durur. Kaldırılmış ilanın
 * gerekçesi satırda görünür.
 */
export default async function AdminProductsPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly status?: string; readonly page?: string }>;
}) {
  const { status: rawStatus, page } = await searchParams;
  const status = adminProductStatuses.find((candidate) => candidate === rawStatus) ?? "PUBLISHED";
  const t = await getTranslations();
  const { accessToken } = await requireRole("/admin/products", "ROLE_ADMIN");
  const result = await listProductsForAdmin(accessToken, status, parsePage(page), PAGE_SIZE);

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <AdminNav current="products" />
      <header className="mb-6 flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t("admin.products.title")}</h1>
        <p className="text-pretty text-muted-foreground">{t("admin.products.intro")}</p>
      </header>

      <FilterTabs
        label={t("admin.products.tabsLabel")}
        pathname="/admin/products"
        current={status}
        options={adminProductStatuses.map((candidate) => ({ value: candidate, label: t("admin.products.tab", { status: candidate }) }))}
      />

      {result.items.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-start gap-3">
            <Inbox aria-hidden className="size-6 text-muted-foreground" />
            <h2 className="text-lg font-semibold tracking-tight">{t("admin.products.emptyTitle")}</h2>
            <p className="text-pretty text-muted-foreground">{t("admin.products.emptyBody")}</p>
          </CardContent>
        </Card>
      ) : (
        <ul className="flex flex-col gap-3">
          {result.items.map((product) => (
            <li key={product.id} className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 sm:flex-row sm:items-start">
              {product.coverPhoto !== undefined && product.coverPhoto !== null && (
                <Image
                  src={photoUrl(product.coverPhoto.cardKey)}
                  alt={product.name}
                  width={cardSize(product.coverPhoto).width}
                  height={cardSize(product.coverPhoto).height}
                  unoptimized
                  className="aspect-square w-full rounded-lg object-cover sm:w-24"
                />
              )}
              <div className="flex min-w-0 flex-1 flex-col gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium break-words">{product.name}</span>
                  <ListingStatusBadge status={product.status} />
                </div>
                <span className="text-sm text-muted-foreground">
                  {product.seller.displayName} · {product.seller.city}
                </span>
                {product.statusReason !== undefined && product.statusReason !== null && (
                  <span className="text-sm text-pretty">{t("admin.products.reason", { reason: product.statusReason })}</span>
                )}
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                  {product.status === "PUBLISHED" && (
                    <Link className={buttonVariants({ variant: "outline", className: "self-start" })} href={`/products/${product.slug}`}>
                      <ExternalLink aria-hidden />
                      {t("admin.products.viewInStore")}
                    </Link>
                  )}
                  <ProductModerationActions product={product} />
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Pagination page={result.page} totalPages={result.totalPages} pathname="/admin/products" query={{ status }} />
    </main>
  );
}

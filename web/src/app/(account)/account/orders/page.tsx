import type { Metadata } from "next";
import Link from "next/link";
import { ArrowLeft, Receipt } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { Pagination, parsePage } from "@/components/Pagination";
import { buttonVariants } from "@/components/ui/button";
import { requireSignedIn } from "@/features/account/requireSession";
import { listOwnOrders } from "@/features/orders/api";
import { OrderStatusBadge } from "@/features/orders/components/OrderStatusBadge";
import { formatDateTime } from "@/lib/datetime";
import { formatMoney } from "@/lib/money";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("orders.title") };
}

const PAGE_SIZE = 20;

/** Siparişlerim (orders spec'i "Alıcı kendi siparişlerini görür"): en yeni üstte. */
export default async function MyOrdersPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly page?: string }>;
}) {
  const { page } = await searchParams;
  const t = await getTranslations();
  const locale = await getLocale();
  const { accessToken } = await requireSignedIn("/account/orders");
  const result = await listOwnOrders(accessToken, parsePage(page), PAGE_SIZE);

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href="/account">
        <ArrowLeft aria-hidden />
        {t("addresses.back")}
      </Link>
      <h1 className="mb-6 text-2xl font-semibold tracking-tight lg:text-3xl">{t("orders.title")}</h1>
      {result.items.length === 0 ? (
        <section
          aria-labelledby="orders-empty-title"
          className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center"
        >
          <span className="grid size-12 place-items-center rounded-full bg-secondary text-primary">
            <Receipt aria-hidden className="size-6" />
          </span>
          <h2 id="orders-empty-title" className="text-lg font-semibold tracking-tight">
            {t("orders.emptyTitle")}
          </h2>
          <Link className={buttonVariants()} href="/">
            {t("cart.backToStore")}
          </Link>
        </section>
      ) : (
        <ul className="flex flex-col gap-3">
          {result.items.map((order) => (
            <li key={order.orderNumber}>
              <Link
                href={`/account/orders/${encodeURIComponent(order.orderNumber)}`}
                className="flex flex-col gap-2 rounded-xl border border-border bg-card p-4 hover:bg-muted sm:flex-row sm:items-center sm:justify-between"
              >
                <span className="flex flex-col gap-1">
                  <span className="font-medium tabular-nums">{order.orderNumber}</span>
                  <span className="text-sm text-muted-foreground">
                    {formatDateTime(order.createdAt, locale)} · {formatMoney(order.grandTotal, locale)}
                  </span>
                </span>
                <OrderStatusBadge status={order.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={result.page} totalPages={result.totalPages} pathname="/account/orders" />
    </main>
  );
}

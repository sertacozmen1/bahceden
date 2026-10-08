import type { Metadata } from "next";
import Link from "next/link";
import { CalendarClock, Inbox } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { FilterTabs } from "@/components/FilterTabs";
import { Pagination, parsePage } from "@/components/Pagination";
import { requireRole } from "@/features/account/requireSession";
import { isSellerNotApproved, SellerNotApproved } from "@/features/listings/components/SellerNotApproved";
import { listSellerOrders, sellerOrderFilters } from "@/features/orders/api";
import { SellerOrderStatusBadge } from "@/features/orders/components/OrderStatusBadge";
import { SellerNav } from "@/features/seller/components/SellerNav";
import { formatDateTime } from "@/lib/datetime";
import { formatMoney } from "@/lib/money";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("sellerOrders.title") };
}

const PAGE_SIZE = 20;

/**
 * Gelen siparişler (orders spec'i "Satıcı kendi satıcı siparişlerini görür").
 * Varsayılan sekme hazırlanacak (yeni) siparişlerdir; kargoya verme son tarihi
 * ve süre aşımı satırda görünür. Onaylı olmayan satıcı başvuru yönlendirmesi görür.
 */
export default async function SellerOrdersPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly status?: string; readonly page?: string }>;
}) {
  const { status: rawStatus, page } = await searchParams;
  const status = sellerOrderFilters.find((candidate) => candidate === rawStatus) ?? "NEW";
  const t = await getTranslations();
  const locale = await getLocale();
  const { accessToken } = await requireRole("/seller/orders", "ROLE_SELLER");

  let result: Awaited<ReturnType<typeof listSellerOrders>>;
  try {
    result = await listSellerOrders(accessToken, status, parsePage(page), PAGE_SIZE);
  } catch (error) {
    if (isSellerNotApproved(error)) {
      return <SellerNotApproved />;
    }
    throw error;
  }

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <header className="mb-6 flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t("sellerOrders.title")}</h1>
        <p className="max-w-prose text-pretty text-muted-foreground">{t("sellerOrders.intro")}</p>
      </header>
      <SellerNav current="orders" />
      <FilterTabs
        label={t("sellerOrders.tabsLabel")}
        pathname="/seller/orders"
        current={status}
        options={sellerOrderFilters.map((candidate) => ({
          value: candidate,
          label: t("orders.sellerOrderStatus", { status: candidate }),
        }))}
      />
      {result.items.length === 0 ? (
        <section className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center">
          <Inbox aria-hidden className="size-6 text-muted-foreground" />
          <h2 className="text-lg font-semibold tracking-tight">{t("sellerOrders.emptyTitle", { status })}</h2>
        </section>
      ) : (
        <ul className="flex flex-col gap-3">
          {result.items.map((order) => (
            <li key={order.id}>
              <Link
                href={`/seller/orders/${order.id}`}
                className="flex flex-col gap-2 rounded-xl border border-border bg-card p-4 hover:bg-muted sm:flex-row sm:items-center sm:justify-between"
              >
                <span className="flex flex-col gap-1">
                  <span className="font-medium tabular-nums">{order.orderNumber}</span>
                  <span className="text-sm text-muted-foreground tabular-nums">
                    {t("sellerOrders.payout", { amount: formatMoney(order.sellerPayout, locale) })}
                  </span>
                  {order.status === "NEW" && order.dispatchDueAt && (
                    <span
                      className={
                        order.dispatchOverdue
                          ? "inline-flex items-center gap-1 text-sm font-medium text-warning"
                          : "inline-flex items-center gap-1 text-sm text-muted-foreground"
                      }
                    >
                      <CalendarClock aria-hidden className="size-3.5" />
                      {order.dispatchOverdue
                        ? t("orders.dispatchOverdue")
                        : t("orders.dispatchDue", { date: formatDateTime(order.dispatchDueAt, locale) })}
                    </span>
                  )}
                </span>
                <SellerOrderStatusBadge status={order.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={result.page} totalPages={result.totalPages} pathname="/seller/orders" query={{ status }} />
    </main>
  );
}

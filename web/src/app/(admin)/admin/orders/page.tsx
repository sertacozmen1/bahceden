import type { Metadata } from "next";
import Link from "next/link";
import { Inbox } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { FilterTabs } from "@/components/FilterTabs";
import { Pagination, parsePage } from "@/components/Pagination";
import { Card, CardContent } from "@/components/ui/card";
import { requireRole } from "@/features/account/requireSession";
import { adminSellerOrderStatuses, listUndeliveredForAdmin } from "@/features/admin/api";
import { AdminNav } from "@/features/admin/components/AdminNav";
import { SellerOrderStatusBadge } from "@/features/orders/components/OrderStatusBadge";
import { formatDateTime } from "@/lib/datetime";
import { formatMoney } from "@/lib/money";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("admin.orders.title") };
}

const PAGE_SIZE = 20;

/**
 * Admin: teslim edilemeyen satıcı siparişleri (shipments spec'i "Teslim
 * edilemeyen siparişte admin karar verir", add-shipments D7). Varsayılan sekme
 * karar bekleyenlerdir; karara bağlanmış olanlar ayrı sekmelerde izlenir.
 */
export default async function AdminOrdersPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly status?: string; readonly page?: string }>;
}) {
  const { status: rawStatus, page } = await searchParams;
  const status = adminSellerOrderStatuses.find((candidate) => candidate === rawStatus) ?? "DELIVERY_FAILED";
  const t = await getTranslations();
  const locale = await getLocale();
  const { accessToken } = await requireRole("/admin/orders", "ROLE_ADMIN");
  const result = await listUndeliveredForAdmin(accessToken, status, parsePage(page), PAGE_SIZE);

  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <AdminNav current="orders" />
      <header className="mb-6 flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t("admin.orders.title")}</h1>
        <p className="text-pretty text-muted-foreground">{t("admin.orders.intro")}</p>
      </header>

      <FilterTabs
        label={t("admin.orders.tabsLabel")}
        pathname="/admin/orders"
        current={status}
        options={adminSellerOrderStatuses.map((candidate) => ({
          value: candidate,
          label: t("orders.sellerOrderStatus", { status: candidate }),
        }))}
      />

      {result.items.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-start gap-3">
            <Inbox aria-hidden className="size-6 text-muted-foreground" />
            <h2 className="text-lg font-semibold tracking-tight">{t("admin.orders.emptyTitle")}</h2>
            <p className="text-pretty text-muted-foreground">{t("admin.orders.emptyBody")}</p>
          </CardContent>
        </Card>
      ) : (
        <ul className="flex flex-col gap-3">
          {result.items.map((order) => (
            <li key={order.id}>
              <Link
                href={`/admin/orders/${order.id}`}
                className="flex flex-col gap-2 rounded-xl border border-border bg-card p-4 hover:bg-muted sm:flex-row sm:items-center sm:justify-between"
              >
                <span className="flex flex-col gap-1">
                  <span className="font-medium tabular-nums">{order.orderNumber}</span>
                  <span className="text-sm text-muted-foreground break-words">
                    {order.sellerName} · {formatDateTime(order.createdAt, locale)}
                  </span>
                  <span className="text-sm text-muted-foreground tabular-nums">
                    {t("admin.orders.refundable", { amount: formatMoney(order.refundableAmount, locale) })}
                  </span>
                </span>
                <SellerOrderStatusBadge status={order.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}

      <Pagination page={result.page} totalPages={result.totalPages} pathname="/admin/orders" query={{ status }} />
    </main>
  );
}

import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, CalendarClock, MapPin } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { requireRole } from "@/features/account/requireSession";
import { isSellerNotApproved, SellerNotApproved } from "@/features/listings/components/SellerNotApproved";
import { getSellerOrder, type SellerOrderDetail } from "@/features/orders/api";
import { orderBffPaths } from "@/features/orders/bff";
import { CancelSellerOrder } from "@/features/orders/components/CancelSellerOrder";
import { SellerOrderStatusBadge } from "@/features/orders/components/OrderStatusBadge";
import { PrepareShipment } from "@/features/orders/components/PrepareShipment";
import { ShipmentCard } from "@/features/orders/components/ShipmentCard";
import { ClaimCard } from "@/features/claims/components/ClaimCard";
import { SellerClaimActions } from "@/features/claims/components/SellerClaimActions";
import { formatDateTime } from "@/lib/datetime";
import { formatMoney } from "@/lib/money";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("sellerOrders.detailTitle") };
}

/**
 * Satıcının satıcı siparişi (orders spec'i): satırlar, ürün tutarı, komisyon,
 * hakediş, kargoya verme son tarihi ve alıcının yalnızca il ve ilçesi (K10).
 * Başkasının veya ödenmemiş sipariş "bulunamadı" olur. Hazırlanan siparişte
 * birincil eylem "Kargoya hazırla"dır, gönderi açıldıysa gönderi kartı (etiket ve
 * gönderi kodu) üstte durur (add-shipments D3, D4). Kargoya verilmemiş sipariş
 * gerekçeyle iptal edilebilir; yıkıcı eylem en altta durur (Fitts).
 */
export default async function SellerOrderPage({ params }: { readonly params: Promise<{ id: string }> }) {
  const id = Number((await params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    notFound();
  }
  const t = await getTranslations();
  const locale = await getLocale();
  const { accessToken } = await requireRole("/seller/orders", "ROLE_SELLER");

  let order: SellerOrderDetail | null;
  try {
    order = await getSellerOrder(accessToken, id);
  } catch (error) {
    if (isSellerNotApproved(error)) {
      return <SellerNotApproved />;
    }
    throw error;
  }
  if (order === null) {
    notFound();
  }

  return (
    <main className="mx-auto w-full max-w-xl px-4 pt-8 pb-12 lg:pt-12">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href="/seller/orders">
        <ArrowLeft aria-hidden />
        {t("sellerOrders.backToList")}
      </Link>
      <header className="mb-6 flex flex-col gap-3">
        <h1 className="text-2xl font-semibold tracking-tight tabular-nums lg:text-3xl">{order.orderNumber}</h1>
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm text-muted-foreground">
          <SellerOrderStatusBadge status={order.status} />
          <span>{t("orders.placedAt", { date: formatDateTime(order.createdAt, locale) })}</span>
        </div>
      </header>
      <div className="flex flex-col gap-4">
        {order.status === "NEW" && order.dispatchDueAt && (
          <p
            className={
              order.dispatchOverdue
                ? "rounded-lg bg-warning/12 px-3 py-2 text-sm font-medium text-warning"
                : "inline-flex items-center gap-1.5 text-sm text-muted-foreground"
            }
          >
            {!order.dispatchOverdue && <CalendarClock aria-hidden className="size-4" />}
            {order.dispatchOverdue
              ? t("sellerOrders.overdue")
              : t("orders.dispatchDue", { date: formatDateTime(order.dispatchDueAt, locale) })}
          </p>
        )}
        {order.shipment ? (
          <ShipmentCard shipment={order.shipment} labelHref={orderBffPaths.label(order.id)} />
        ) : (
          order.status === "NEW" && <PrepareShipment sellerOrderId={order.id} />
        )}
        {order.claim && (
          <ClaimCard
            claim={order.claim}
            viewer="seller"
            actions={
              order.claim.status === "OPEN" ? <SellerClaimActions sellerOrderId={order.id} claim={order.claim} /> : null
            }
          />
        )}
        {(order.status === "DELIVERY_FAILED" || order.status === "REFUNDED" || order.status === "CLOSED") && (
          <p className="rounded-lg bg-muted px-3 py-2 text-sm text-pretty">
            {t("sellerOrders.shippingNote", { status: order.status })}
          </p>
        )}
        {order.status === "CANCELLED" && (
          <p className="text-sm text-muted-foreground">
            {t("orders.cancelledBy", { by: order.cancelledBy ?? "BUYER" })}
            {order.cancelReason ? ` ${t("orders.cancelReason", { reason: order.cancelReason })}` : ""}
          </p>
        )}
        <Card>
          <CardContent className="flex flex-col gap-4">
            <ul className="flex flex-col divide-y divide-border text-sm">
              {order.items.map((item) => (
                <li key={item.productSlug} className="flex justify-between gap-3 py-2 first:pt-0 last:pb-0">
                  <span className="min-w-0 break-words">
                    <span className="font-medium">{item.productName}</span>{" "}
                    <span className="text-muted-foreground tabular-nums">
                      {formatMoney(item.unitPrice, locale)} {t("orders.perUnit", { unit: item.unit })} × {item.quantity}
                    </span>
                  </span>
                  <span className="shrink-0 tabular-nums">{formatMoney(item.lineTotal, locale)}</span>
                </li>
              ))}
            </ul>
            <dl className="flex flex-col gap-2 border-t border-border pt-3 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">{t("orders.itemsTotal")}</dt>
                <dd className="tabular-nums">{formatMoney(order.itemsTotal, locale)}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">{t("sellerOrders.commission")}</dt>
                <dd className="tabular-nums">−{formatMoney(order.commissionAmount, locale)}</dd>
              </div>
              <div className="flex justify-between gap-3 border-t border-border pt-2 text-base font-semibold">
                <dt>{t("sellerOrders.payoutLabel")}</dt>
                <dd className="tabular-nums">{formatMoney(order.sellerPayout, locale)}</dd>
              </div>
              {order.payoutAfterClaim && (
                <div className="flex justify-between gap-3 text-base font-semibold text-warning">
                  <dt>{t("sellerOrders.payoutAfterClaim")}</dt>
                  <dd className="tabular-nums">{formatMoney(order.payoutAfterClaim, locale)}</dd>
                </div>
              )}
            </dl>
          </CardContent>
        </Card>
        <p className="inline-flex items-center gap-1.5 text-sm text-muted-foreground">
          <MapPin aria-hidden className="size-4" />
          {t("sellerOrders.destination", { district: order.district, province: order.provinceName })}
        </p>
        {order.cancellable && <CancelSellerOrder as="seller" sellerOrderId={order.id} />}
      </div>
    </main>
  );
}

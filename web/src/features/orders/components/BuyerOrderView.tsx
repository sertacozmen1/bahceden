import Link from "next/link";
import { CalendarClock, MapPin, MessageSquareWarning } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { claimBffPaths } from "@/features/claims/bff";
import { BuyerClaimActions } from "@/features/claims/components/BuyerClaimActions";
import { ClaimCard } from "@/features/claims/components/ClaimCard";
import type { BuyerSellerOrderView, OrderDetail } from "@/features/orders/api";
import { formatDateTime } from "@/lib/datetime";
import { formatMoney } from "@/lib/money";
import { OrderStatusBadge, SellerOrderStatusBadge } from "./OrderStatusBadge";
import { ShipmentCard } from "./ShipmentCard";

/**
 * Alıcının sipariş detayı (orders spec'i): satıcı siparişleri, satırlar,
 * kargo ücreti, kargoya verme son tarihi, gönderi (firma, takip numarası,
 * durum; add-shipments), teslimat adresi ve toplam. Sipariş
 * sayfası ve ödeme dönüşü paylaşır (K9). Satıcı siparişinin eylemi (iptal)
 * çağıran sayfadan `sellerOrderAction` ile verilir. Teslim edilen satıcı
 * siparişinde süre dolmadıysa "Sorun bildir" bağlantısı, talep varsa talep
 * kartı görünür (ADR-086).
 */
type BuyerOrderViewProps = {
  readonly order: OrderDetail;
  readonly sellerOrderAction?: (sellerOrder: BuyerSellerOrderView) => React.ReactNode;
};

export function BuyerOrderView({ order, sellerOrderAction }: BuyerOrderViewProps) {
  const t = useTranslations("orders");
  const tClaims = useTranslations("claims");
  const locale = useLocale();
  const address = order.shippingAddress;
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm text-muted-foreground">
        <OrderStatusBadge status={order.status} />
        <span>{t("placedAt", { date: formatDateTime(order.createdAt, locale) })}</span>
      </div>
      <ul className="flex flex-col gap-4">
        {order.sellerOrders.map((sellerOrder) => (
          <li key={sellerOrder.id}>
            <Card>
              <CardContent className="flex flex-col gap-4">
                <header className="flex flex-wrap items-center justify-between gap-2">
                  <h2 className="font-semibold tracking-tight break-words">{sellerOrder.seller.displayName}</h2>
                  <SellerOrderStatusBadge status={sellerOrder.status} />
                </header>
                <ul className="flex flex-col divide-y divide-border text-sm">
                  {sellerOrder.items.map((item) => (
                    <li key={item.productSlug} className="flex justify-between gap-3 py-2 first:pt-0 last:pb-0">
                      <span className="min-w-0 break-words">
                        <Link className="font-medium hover:underline" href={`/products/${item.productSlug}`}>
                          {item.productName}
                        </Link>{" "}
                        <span className="text-muted-foreground tabular-nums">
                          {formatMoney(item.unitPrice, locale)} {t("perUnit", { unit: item.unit })} × {item.quantity}
                        </span>
                      </span>
                      <span className="shrink-0 tabular-nums">{formatMoney(item.lineTotal, locale)}</span>
                    </li>
                  ))}
                </ul>
                <dl className="flex flex-col gap-1 border-t border-border pt-3 text-sm">
                  <div className="flex justify-between gap-3">
                    <dt className="text-muted-foreground">{t("shipping")}</dt>
                    <dd className="tabular-nums">
                      {sellerOrder.shippingFee.amount === 0 ? t("freeShipping") : formatMoney(sellerOrder.shippingFee, locale)}
                    </dd>
                  </div>
                </dl>
                {sellerOrder.dispatchDueAt && sellerOrder.status === "NEW" && (
                  <p className="inline-flex items-center gap-1.5 text-sm text-muted-foreground">
                    <CalendarClock aria-hidden className="size-4" />
                    {t("dispatchDue", { date: formatDateTime(sellerOrder.dispatchDueAt, locale) })}
                  </p>
                )}
                {sellerOrder.dispatchOverdue && (
                  <p className="rounded-lg bg-warning/12 px-3 py-2 text-sm text-warning">{t("dispatchOverdue")}</p>
                )}
                {sellerOrder.shipment && <ShipmentCard shipment={sellerOrder.shipment} />}
                {(sellerOrder.status === "DELIVERY_FAILED" ||
                  sellerOrder.status === "REFUNDED" ||
                  sellerOrder.status === "CLOSED") && (
                  <p className="rounded-lg bg-muted px-3 py-2 text-sm text-pretty">
                    {t("shippingNote", { status: sellerOrder.status })}
                  </p>
                )}
                {sellerOrder.claim && (
                  <ClaimCard
                    claim={sellerOrder.claim}
                    viewer="buyer"
                    actions={
                      sellerOrder.claim.status === "OFFERED" || sellerOrder.claim.status === "REJECTED" ? (
                        <BuyerClaimActions orderNumber={order.orderNumber} sellerOrderId={sellerOrder.id} />
                      ) : null
                    }
                  />
                )}
                {sellerOrder.claimableUntil && (
                  <div className="flex flex-col gap-2 rounded-lg bg-muted px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-pretty">
                      {tClaims("reportUntil", { date: formatDateTime(sellerOrder.claimableUntil, locale) })}
                    </p>
                    <Link
                      className={buttonVariants({ variant: "outline", className: "shrink-0" })}
                      href={claimBffPaths.form(order.orderNumber, sellerOrder.id)}
                    >
                      <MessageSquareWarning aria-hidden />
                      {tClaims("report")}
                    </Link>
                  </div>
                )}
                {sellerOrder.status === "CANCELLED" && (
                  <p className="text-sm text-muted-foreground">
                    {t("cancelledBy", { by: sellerOrder.cancelledBy ?? "BUYER" })}
                    {sellerOrder.cancelReason ? ` ${t("cancelReason", { reason: sellerOrder.cancelReason })}` : ""}
                  </p>
                )}
                {sellerOrderAction?.(sellerOrder)}
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>
      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <CardContent className="flex flex-col gap-1 text-sm">
            <h2 className="mb-1 inline-flex items-center gap-1.5 font-semibold tracking-tight">
              <MapPin aria-hidden className="size-4" />
              {t("deliveryAddress")}
            </h2>
            <span>
              {address.recipientName} · {address.phone}
            </span>
            <span className="break-words">{address.addressLine}</span>
            <span>
              {address.district} / {address.provinceName}
              {address.postalCode ? ` ${address.postalCode}` : ""}
            </span>
          </CardContent>
        </Card>
        <Card>
          <CardContent>
            <dl className="flex flex-col gap-2 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">{t("itemsTotal")}</dt>
                <dd className="tabular-nums">{formatMoney(order.itemsTotal, locale)}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">{t("shipping")}</dt>
                <dd className="tabular-nums">{formatMoney(order.shippingTotal, locale)}</dd>
              </div>
              <div className="flex justify-between gap-3 border-t border-border pt-2 text-base font-semibold">
                <dt>{t("grandTotal")}</dt>
                <dd className="tabular-nums">{formatMoney(order.grandTotal, locale)}</dd>
              </div>
            </dl>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

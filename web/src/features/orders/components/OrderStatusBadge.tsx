import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import type { OrderStatus, SellerOrderStatus } from "@/features/orders/api";

/**
 * Sipariş ve satıcı siparişi durumu etiketi. Renk tek başına anlam taşımaz;
 * durum her zaman metinle söylenir (AGENTS.md §12.4).
 */
const orderVariants = {
  PENDING_PAYMENT: "warning",
  PAID: "success",
  PAYMENT_FAILED: "destructive",
  PAYMENT_EXPIRED: "secondary",
  CANCELLED_OUT_OF_STOCK: "destructive",
} as const satisfies Record<OrderStatus, "warning" | "success" | "destructive" | "secondary">;

const sellerOrderVariants = {
  PENDING_PAYMENT: "warning",
  NEW: "info",
  SHIPPED: "info",
  DELIVERED: "success",
  DELIVERY_FAILED: "destructive",
  REFUNDED: "secondary",
  CLOSED: "secondary",
  CANCELLED: "secondary",
  VOID: "secondary",
} as const satisfies Record<SellerOrderStatus, "warning" | "info" | "success" | "destructive" | "secondary">;

export function OrderStatusBadge({ status }: { readonly status: OrderStatus }) {
  const t = useTranslations("orders");
  return <Badge variant={orderVariants[status]}>{t("status", { status })}</Badge>;
}

export function SellerOrderStatusBadge({ status }: { readonly status: SellerOrderStatus }) {
  const t = useTranslations("orders");
  return <Badge variant={sellerOrderVariants[status]}>{t("sellerOrderStatus", { status })}</Badge>;
}

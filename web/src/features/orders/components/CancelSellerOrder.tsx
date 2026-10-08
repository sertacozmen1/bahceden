"use client";

import { useRouter } from "next/navigation";
import { CircleX } from "lucide-react";
import { useTranslations } from "next-intl";
import { ReasonDialog } from "@/components/ReasonDialog";
import { orderBffPaths } from "@/features/orders/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Kargodan önce iptal (orders spec'i, ADR-056 §1, add-cart-and-checkout D9).
 * Yıkıcı eylem onay penceresiyle yapılır (Fitts). Alıcı için gerekçe isteğe
 * bağlıdır, satıcı için zorunludur. Başarıda sayfa sunucudan yenilenir; iade
 * tutarı ve stok `api`'de hesaplanır.
 */
type CancelSellerOrderProps =
  | { readonly as: "buyer"; readonly orderNumber: string; readonly sellerOrderId: number; readonly sellerName: string }
  | { readonly as: "seller"; readonly sellerOrderId: number };

export function CancelSellerOrder(props: CancelSellerOrderProps) {
  const t = useTranslations("orders.cancel");
  const router = useRouter();
  const buyer = props.as === "buyer";
  return (
    <ReasonDialog
      id={`seller-order-${props.sellerOrderId}`}
      icon={CircleX}
      triggerLabel={t("action")}
      title={buyer ? t("buyerTitle", { seller: props.sellerName }) : t("sellerTitle")}
      description={buyer ? t("buyerBody") : t("sellerBody")}
      confirmLabel={t("confirm")}
      reasonOptional={buyer}
      onSubmit={async (reason) => {
        const path = buyer
          ? orderBffPaths.buyerCancel(props.orderNumber, props.sellerOrderId)
          : orderBffPaths.sellerCancel(props.sellerOrderId);
        await postToBff(path, { reason: reason === "" ? null : reason });
        router.refresh();
      }}
    />
  );
}

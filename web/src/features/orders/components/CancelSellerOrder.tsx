"use client";

import { useRef } from "react";
import { useRouter } from "next/navigation";
import { CircleX } from "lucide-react";
import { useTranslations } from "next-intl";
import { ReasonDialog } from "@/components/ReasonDialog";
import { orderBffPaths } from "@/features/orders/bff";
import { ApiRequestError } from "@/lib/apiError";
import { postToBff } from "@/lib/bffClient";

/**
 * Kargodan önce iptal (orders spec'i, ADR-056 §1, add-cart-and-checkout D9).
 * Yıkıcı eylem onay penceresiyle yapılır (Fitts). Alıcı için gerekçe isteğe
 * bağlıdır, satıcı için zorunludur. Başarıda sayfa sunucudan yenilenir; iade
 * tutarı ve stok `api`'de hesaplanır.
 *
 * Paket durum sorgusundan önce kargoya verilmişse `api` iptali
 * `ORDER_ALREADY_SHIPPED` ile reddeder ve siparişi "Kargoda"ya geçirir
 * (change-cancel-rejection-syncs-shipment D2). Mesaj pencerede kalır; sayfa
 * pencere kapanınca yenilenir, çünkü yenileme iptal düğmesini ve onunla
 * pencereyi kaldırır.
 */
type CancelSellerOrderProps =
  | { readonly as: "buyer"; readonly orderNumber: string; readonly sellerOrderId: number; readonly sellerName: string }
  | { readonly as: "seller"; readonly sellerOrderId: number };

export function CancelSellerOrder(props: CancelSellerOrderProps) {
  const t = useTranslations("orders.cancel");
  const router = useRouter();
  const buyer = props.as === "buyer";
  const staleAfterClose = useRef(false);
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
        try {
          await postToBff(path, { reason: reason === "" ? null : reason });
        } catch (cause) {
          if (cause instanceof ApiRequestError && cause.code === "ORDER_ALREADY_SHIPPED") {
            staleAfterClose.current = true;
          }
          throw cause;
        }
        router.refresh();
      }}
      onClose={() => {
        if (staleAfterClose.current) {
          staleAfterClose.current = false;
          router.refresh();
        }
      }}
    />
  );
}

"use client";

import { useRouter } from "next/navigation";
import { Archive, Undo2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { ReasonDialog } from "@/components/ReasonDialog";
import { adminBffPaths } from "@/features/admin/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Teslim edilemeyen sipariş için admin kararı (shipments spec'i, add-shipments
 * D7). İki eylem de para veya hakedişi kesinleştirdiği için onay penceresiyle
 * yapılır (Fitts): iadede açıklama isteğe bağlı, kapatmada gerekçe zorunludur.
 * Açıklama alıcıya ve satıcıya e-postayla iletilir. Otorite `api`'dir.
 */
export function UndeliveredResolutionActions({
  sellerOrderId,
  refundAmount,
}: {
  readonly sellerOrderId: number;
  /** Gösterim için biçimlenmiş iade tutarı; hesap `api`'de. */
  readonly refundAmount: string;
}) {
  const t = useTranslations("admin.orders.actions");
  const router = useRouter();

  async function resolve(decision: "REFUND" | "CLOSE", reason: string) {
    await postToBff(adminBffPaths.orderResolution(sellerOrderId), { decision, reason });
    router.refresh();
  }

  return (
    <div className="flex flex-col gap-3 sm:flex-row">
      <ReasonDialog
        id={`refund-${sellerOrderId}`}
        icon={Undo2}
        triggerLabel={t("refund")}
        title={t("refundTitle")}
        description={t("refundBody", { amount: refundAmount })}
        confirmLabel={t("confirmRefund")}
        reasonOptional
        onSubmit={(reason) => resolve("REFUND", reason)}
      />
      <ReasonDialog
        id={`close-${sellerOrderId}`}
        icon={Archive}
        triggerLabel={t("close")}
        title={t("closeTitle")}
        description={t("closeBody")}
        confirmLabel={t("confirmClose")}
        onSubmit={(reason) => resolve("CLOSE", reason)}
      />
    </div>
  );
}

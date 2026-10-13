"use client";

import { useRouter } from "next/navigation";
import { CircleX, HandCoins } from "lucide-react";
import { useTranslations } from "next-intl";
import { ReasonDialog } from "@/components/ReasonDialog";
import type { AdminClaimDetail } from "@/features/claims/api";
import { claimBffPaths } from "@/features/claims/bff";
import { postToBff } from "@/lib/bffClient";
import { ClaimSettlementDialog, linesOf, type Quantities } from "./ClaimSettlementDialog";

/**
 * Admin kararı (ADR-080, ADR-086): satır başına adetle iade (satın alınana kadar;
 * başlangıç alıcının istediği adet) ve gerekçeli ret. İadenin para etkisi
 * sunucudan önizlenir; ret onay penceresinde gerekçe ister.
 */
export function AdminClaimDecision({ detail }: { readonly detail: AdminClaimDetail }) {
  const t = useTranslations("admin.claims.actions");
  const router = useRouter();
  const purchased = new Map(detail.items.map((item) => [item.id, item.quantity]));
  const initial: Quantities = new Map(detail.claim.lines.map((line) => [line.orderItemId, line.claimedQuantity]));
  const lines = detail.claim.lines.map((line) => ({
    orderItemId: line.orderItemId,
    productName: line.productName,
    max: purchased.get(line.orderItemId) ?? line.claimedQuantity,
  }));

  async function decide(body: Record<string, unknown>) {
    await postToBff(claimBffPaths.adminDecision(detail.claim.id), body);
    router.refresh();
  }

  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
      <ClaimSettlementDialog
        previewPath={claimBffPaths.adminPreview(detail.claim.id)}
        trigger={t("refund")}
        title={t("refundTitle")}
        description={t("refundBody")}
        confirm={t("confirmRefund")}
        icon={<HandCoins aria-hidden />}
        primary
        initial={initial}
        lines={lines}
        lineLabel={(name) => t("lineLabel", { name })}
        onConfirm={(quantities) => decide({ decision: "REFUND", lines: linesOf(quantities) })}
      />
      <ReasonDialog
        id={`admin-claim-${detail.claim.id}`}
        icon={CircleX}
        triggerLabel={t("reject")}
        title={t("rejectTitle")}
        description={t("rejectBody")}
        confirmLabel={t("confirmReject")}
        onSubmit={(reason) => decide({ decision: "REJECT", reason })}
      />
    </div>
  );
}

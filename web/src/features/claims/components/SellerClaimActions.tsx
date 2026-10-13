"use client";

import { useRouter } from "next/navigation";
import { CircleCheck, CircleX, Scale } from "lucide-react";
import { useTranslations } from "next-intl";
import { ReasonDialog } from "@/components/ReasonDialog";
import type { DefectClaimView } from "@/features/claims/api";
import { claimBffPaths } from "@/features/claims/bff";
import { postToBff } from "@/lib/bffClient";
import { ClaimSettlementDialog, linesOf, type Quantities } from "./ClaimSettlementDialog";

/**
 * Satıcının talebe yanıtı (ADR-080, ADR-086): tamamını kabul (birincil), adetli
 * kısmi teklif ve gerekçeli ret (yıkıcı, en sonda). Her para hareketi onay
 * penceresinde, sunucunun önizlediği tutarla söylenir: "Alıcıya ₺X iade edilir,
 * hakedişiniz ₺Y olur." Tutarı ekran hesaplamaz (AGENTS.md §12.1).
 */
type Props = {
  readonly sellerOrderId: number;
  readonly claim: DefectClaimView;
};

export function SellerClaimActions({ sellerOrderId, claim }: Props) {
  const t = useTranslations("claims.actions");
  const router = useRouter();
  const claimed: Quantities = new Map(claim.lines.map((line) => [line.orderItemId, line.claimedQuantity]));
  const offerLines = claim.lines.map((line) => ({
    orderItemId: line.orderItemId,
    productName: line.productName,
    max: line.claimedQuantity,
  }));
  const previewPath = claimBffPaths.sellerPreview(sellerOrderId);
  const lineLabel = (name: string) => t("offerLine", { name });

  async function respond(body: Record<string, unknown>) {
    await postToBff(claimBffPaths.sellerResponse(sellerOrderId), body);
    router.refresh();
  }

  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
      <ClaimSettlementDialog
        previewPath={previewPath}
        trigger={t("accept")}
        title={t("acceptTitle")}
        description={t("acceptBody")}
        confirm={t("confirmAccept")}
        icon={<CircleCheck aria-hidden />}
        primary
        initial={claimed}
        lines={[]}
        lineLabel={lineLabel}
        onConfirm={() => respond({ decision: "ACCEPT" })}
      />
      <ClaimSettlementDialog
        previewPath={previewPath}
        trigger={t("offer")}
        title={t("offerTitle")}
        description={t("offerBody")}
        confirm={t("confirmOffer")}
        icon={<Scale aria-hidden />}
        primary={false}
        initial={claimed}
        lines={offerLines}
        lineLabel={lineLabel}
        onConfirm={(quantities) => respond({ decision: "OFFER", lines: linesOf(quantities) })}
      />
      <ReasonDialog
        id={`claim-${claim.id}`}
        icon={CircleX}
        triggerLabel={t("reject")}
        title={t("rejectTitle")}
        description={t("rejectBody")}
        confirmLabel={t("confirmReject")}
        onSubmit={(reason) => respond({ decision: "REJECT", reason })}
      />
    </div>
  );
}

"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import type { BuyerClaimAction } from "@/features/claims/api";
import { claimBffPaths } from "@/features/claims/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Alıcının teklife veya redde yanıtı (ADR-080, ADR-086): "Kabul et" birincil,
 * "Bahçeden ekibine taşı" ikincil. Sonuç sunucudan gelir; sayfa yenilenir.
 */
type Props = {
  readonly orderNumber: string;
  readonly sellerOrderId: number;
};

export function BuyerClaimActions({ orderNumber, sellerOrderId }: Props) {
  const t = useTranslations("claims.actions");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [pending, setPending] = useState<BuyerClaimAction | undefined>(undefined);
  const [error, setError] = useState<string | undefined>(undefined);

  async function act(action: BuyerClaimAction) {
    setPending(action);
    setError(undefined);
    try {
      await postToBff(claimBffPaths.buyerAction(orderNumber, sellerOrderId, action));
      router.refresh();
    } catch (cause) {
      setError(toFormErrors(cause, errorTexts).general);
    } finally {
      setPending(undefined);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {error !== undefined && <FormMessage tone="error">{error}</FormMessage>}
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button type="button" disabled={pending !== undefined} onClick={() => act("accept")}>
          {pending === "accept" ? t("sending") : t("buyerAccept")}
        </Button>
        <Button type="button" variant="outline" disabled={pending !== undefined} onClick={() => act("escalate")}>
          {pending === "escalate" ? t("sending") : t("buyerEscalate")}
        </Button>
      </div>
    </div>
  );
}

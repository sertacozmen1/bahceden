"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { BadgeCheck, Ban, PauseCircle, PlayCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import { ReasonDialog } from "@/components/ReasonDialog";
import { Button } from "@/components/ui/button";
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import type { SellerDecision, SellerStatus } from "@/features/admin/api";
import { adminBffPaths } from "@/features/admin/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Admin karar eylemleri (seller-onboarding spec'i "Admin başvuruları karara
 * bağlar", AGENTS.md §12.5).
 *
 * Yalnızca kaydın şimdiki durumundan yapılabilen eylemler görünür; otorite
 * yine `api`'dir (geçersiz geçiş 409). Onay birincil ve büyüktür; ret ve askı
 * yıkıcı eylemlerdir, ayrı durur ve gerekçe isteyen bir onay penceresinden
 * geçer (Fitts). Başarıda sayfa sunucudan yeniden okunur.
 */

type Props = { readonly sellerId: number; readonly status: SellerStatus };

export function SellerDecisionActions({ sellerId, status }: Props) {
  const t = useTranslations("admin.sellers.actions");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [pending, setPending] = useState<SellerDecision | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function decide(decision: SellerDecision) {
    setPending(decision);
    setError(null);
    try {
      await postToBff(adminBffPaths.decision(sellerId, decision));
      router.refresh();
    } catch (cause) {
      setError(toFormErrors(cause, errorTexts).general ?? errorTexts.unavailable);
    } finally {
      setPending(null);
    }
  }

  if (status === "REJECTED") {
    return null;
  }

  return (
    <div className="flex flex-col gap-3">
      {error !== null && <FormMessage tone="error">{error}</FormMessage>}
      {status === "PENDING" && (
        <div className="flex flex-col gap-3 sm:flex-row sm:justify-between">
          <Button size="lg" type="button" onClick={() => void decide("approve")} disabled={pending !== null}>
            <BadgeCheck aria-hidden />
            {pending === "approve" ? t("approving") : t("approve")}
          </Button>
          <SellerReasonDialog sellerId={sellerId} decision="reject" />
        </div>
      )}
      {status === "APPROVED" && <SellerReasonDialog sellerId={sellerId} decision="suspend" />}
      {status === "SUSPENDED" && (
        <Button
          variant="secondary"
          type="button"
          className="self-start"
          onClick={() => void decide("reinstate")}
          disabled={pending !== null}
        >
          <PlayCircle aria-hidden />
          {pending === "reinstate" ? t("reinstating") : t("reinstate")}
        </Button>
      )}
    </div>
  );
}

/** Gerekçe isteyen yıkıcı eylem: ret veya askı. Diyaloğun kendisi ortak bileşendedir. */
function SellerReasonDialog({ sellerId, decision }: { readonly sellerId: number; readonly decision: "reject" | "suspend" }) {
  const t = useTranslations("admin.sellers.actions");
  const router = useRouter();
  return (
    <ReasonDialog
      id={`seller-${decision}`}
      icon={decision === "reject" ? Ban : PauseCircle}
      triggerLabel={decision === "reject" ? t("reject") : t("suspend")}
      title={decision === "reject" ? t("rejectTitle") : t("suspendTitle")}
      description={decision === "reject" ? t("rejectBody") : t("suspendBody")}
      confirmLabel={decision === "reject" ? t("confirmReject") : t("confirmSuspend")}
      onSubmit={async (reason) => {
        await postToBff(adminBffPaths.decision(sellerId, decision), { reason });
        router.refresh();
      }}
    />
  );
}

"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { BadgeCheck, Ban, PauseCircle, PlayCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { FormMessage } from "@/features/account/components/FormMessage";
import { noErrors, toFormErrors, type FormErrors, useFormErrorTexts } from "@/features/account/formErrors";
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

/** Sunucudaki kısıtla aynı (SellerDecisionReason): yalnızca erken uyarı. */
const MIN_REASON_LENGTH = 10;
const MAX_REASON_LENGTH = 500;

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
          <ReasonDialog sellerId={sellerId} decision="reject" />
        </div>
      )}
      {status === "APPROVED" && <ReasonDialog sellerId={sellerId} decision="suspend" />}
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

/** Gerekçe isteyen yıkıcı eylem: ret veya askı. */
function ReasonDialog({ sellerId, decision }: { readonly sellerId: number; readonly decision: "reject" | "suspend" }) {
  const t = useTranslations("admin.sellers.actions");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const Icon = decision === "reject" ? Ban : PauseCircle;
  const reasonId = `decision-reason-${decision}`;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const reason = String(new FormData(event.currentTarget).get("reason") ?? "").trim();
    if (reason.length < MIN_REASON_LENGTH) {
      setErrors({ fields: { reason: t("reasonRequired") } });
      return;
    }
    setPending(true);
    setErrors(noErrors);
    try {
      await postToBff(adminBffPaths.decision(sellerId, decision), { reason });
      setOpen(false);
      router.refresh();
    } catch (cause) {
      setErrors(toFormErrors(cause, errorTexts));
    } finally {
      setPending(false);
    }
  }

  const reasonError = errors.fields.reason;
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button variant="destructive" type="button" className="self-start" />}>
        <Icon aria-hidden />
        {decision === "reject" ? t("reject") : t("suspend")}
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
          <DialogHeader>
            <DialogTitle>{decision === "reject" ? t("rejectTitle") : t("suspendTitle")}</DialogTitle>
            <DialogDescription>{decision === "reject" ? t("rejectBody") : t("suspendBody")}</DialogDescription>
          </DialogHeader>
          {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
          <Field data-invalid={reasonError !== undefined}>
            <FieldLabel htmlFor={reasonId}>{t("reason")}</FieldLabel>
            <Textarea
              id={reasonId}
              name="reason"
              maxLength={MAX_REASON_LENGTH}
              required
              aria-invalid={reasonError !== undefined}
              aria-describedby={reasonError === undefined ? `${reasonId}-ipucu` : `${reasonId}-ipucu ${reasonId}-hata`}
            />
            <FieldDescription id={`${reasonId}-ipucu`}>{t("reasonHint")}</FieldDescription>
            {reasonError !== undefined && <FieldError id={`${reasonId}-hata`}>{reasonError}</FieldError>}
          </Field>
          <DialogFooter>
            <DialogClose render={<Button variant="ghost" type="button" />}>{t("cancel")}</DialogClose>
            <Button variant="destructive" type="submit" disabled={pending}>
              {pending ? t("sending") : decision === "reject" ? t("confirmReject") : t("confirmSuspend")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

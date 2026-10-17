"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
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
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import { QuantityStepper } from "@/features/cart/components/QuantityStepper";
import type { ClaimSettlementView } from "@/features/claims/api";
import { postToBff } from "@/lib/bffClient";
import { formatMoney } from "@/lib/money";

/**
 * İade kararının para etkisini sunucudan önizleyen onay penceresi (ADR-086).
 * Satıcının kabulü ve teklifi ile admin'in iadesi paylaşır (K9). Düzenlenebilir
 * pencerede satır başına adet seçilir (0…en çok); her değişiklik kısa bir
 * beklemeden sonra yeniden önizlenir. Sunucunun reddettiği adet (ör. toplam
 * sıfır) pencerede söylenir ve onay kapalı kalır. Tutarı ekran hesaplamaz
 * (AGENTS.md §12.1).
 */
export type Quantities = ReadonlyMap<number, number>;

export function linesOf(quantities: Quantities) {
  return Array.from(quantities.entries()).map(([orderItemId, quantity]) => ({ orderItemId, quantity }));
}

/** Önizleme isteğinin adet değişikliklerinden sonra beklediği süre. */
const PREVIEW_DELAY_MS = 300;

type Line = {
  readonly orderItemId: number;
  readonly productName: string;
  readonly max: number;
};

type Props = {
  readonly previewPath: string;
  readonly trigger: string;
  readonly title: string;
  readonly description: string;
  readonly confirm: string;
  readonly icon: React.ReactNode;
  readonly primary: boolean;
  readonly initial: Quantities;
  /** Düzenlenebilir satırlar; boşsa pencere yalnızca önizler (satırsız önizleme: istenenin tamamı). */
  readonly lines: readonly Line[];
  readonly lineLabel: (productName: string) => string;
  readonly onConfirm: (quantities: Quantities) => Promise<void>;
};

export function ClaimSettlementDialog(props: Props) {
  const t = useTranslations("claims.actions");
  const locale = useLocale();
  const errorTexts = useFormErrorTexts();
  // Metinler bağımlılık listesinde ilkel değer olarak durur: nesne her render'da yeniden oluşur.
  const { unavailable, formHasErrors } = errorTexts;
  const editable = props.lines.length > 0;
  const [open, setOpen] = useState(false);
  const [quantities, setQuantities] = useState<Quantities>(props.initial);
  const [settlement, setSettlement] = useState<ClaimSettlementView | undefined>(undefined);
  const [error, setError] = useState<string | undefined>(undefined);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    let cancelled = false;
    const timer = setTimeout(async () => {
      try {
        const result = await postToBff<ClaimSettlementView>(props.previewPath, {
          lines: editable ? linesOf(quantities) : [],
        });
        if (!cancelled) {
          setSettlement(result);
          setError(undefined);
        }
      } catch (cause) {
        if (!cancelled) {
          setSettlement(undefined);
          const errors = toFormErrors(cause, { unavailable, formHasErrors });
          setError(Object.values(errors.fields)[0] ?? errors.general);
        }
      }
    }, PREVIEW_DELAY_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [open, quantities, editable, props.previewPath, unavailable, formHasErrors]);

  async function handleConfirm() {
    setPending(true);
    setError(undefined);
    try {
      await props.onConfirm(quantities);
      setOpen(false);
    } catch (cause) {
      const errors = toFormErrors(cause, errorTexts);
      setError(Object.values(errors.fields)[0] ?? errors.general);
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) {
          setQuantities(props.initial);
          setSettlement(undefined);
        }
      }}
    >
      <DialogTrigger render={<Button variant={props.primary ? "default" : "outline"} type="button" />}>
        {props.icon}
        {props.trigger}
      </DialogTrigger>
      <DialogContent>
        <div className="flex flex-col gap-5">
          <DialogHeader>
            <DialogTitle>{props.title}</DialogTitle>
            <DialogDescription>{props.description}</DialogDescription>
          </DialogHeader>
          {editable && (
            <ul className="flex flex-col gap-3">
              {props.lines.map((line) => (
                <li key={line.orderItemId} className="flex flex-wrap items-center justify-between gap-3">
                  <span className="min-w-0 font-medium break-words">{line.productName}</span>
                  <QuantityStepper
                    value={quantities.get(line.orderItemId) ?? 0}
                    min={0}
                    max={line.max}
                    label={props.lineLabel(line.productName)}
                    onChange={(quantity) => setQuantities(new Map(quantities).set(line.orderItemId, quantity))}
                  />
                </li>
              ))}
            </ul>
          )}
          <div role="status" className="rounded-lg bg-muted px-3 py-2 text-sm text-pretty">
            {settlement === undefined ? (
              error === undefined && t("calculating")
            ) : (
              <>
                {t("settlement", {
                  refund: formatMoney(settlement.refundAmount, locale),
                  payout: formatMoney(settlement.payoutAfter, locale),
                })}
                {settlement.shippingRefunded && <span className="mt-1 block">{t("settlementShipping")}</span>}
              </>
            )}
          </div>
          {error !== undefined && <FormMessage tone="error">{error}</FormMessage>}
          <DialogFooter>
            <DialogClose render={<Button variant="ghost" type="button" />}>{t("cancel")}</DialogClose>
            <Button type="button" onClick={handleConfirm} disabled={pending || settlement === undefined}>
              {pending ? t("sending") : props.confirm}
            </Button>
          </DialogFooter>
        </div>
      </DialogContent>
    </Dialog>
  );
}

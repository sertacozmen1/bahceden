"use client";

import { useState, type FormEvent } from "react";
import type { LucideIcon } from "lucide-react";
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

/**
 * Gerekçe isteyen yıkıcı eylem (satıcı reddi ve askısı, ilan kaldırma,
 * sipariş iptali; add-product-listings D7, add-cart-and-checkout D10). Bütün
 * ekranlar aynı davranışı paylaşır (K9).
 *
 * Gerekçe karşı tarafa olduğu gibi gösterilir; sınırlar sunucudakiyle aynıdır
 * (10–500), istemcideki denetim yalnızca erken uyarıdır. `reasonOptional`
 * ile gerekçe boş bırakılabilir (alıcının iptali; ADR-056 §1). Gönderim başarılıysa
 * pencere kapanır; sunucunun alan hatası alana bağlanır.
 */

const MIN_REASON_LENGTH = 10;
const MAX_REASON_LENGTH = 500;

type Props = {
  /** Diyaloğun kimliği; aynı sayfada birden fazla diyalog olabilir. */
  readonly id: string;
  readonly icon: LucideIcon;
  readonly triggerLabel: string;
  readonly title: string;
  readonly description: string;
  readonly confirmLabel: string;
  /** Gerekçeyle eylemi gönderir (isteğe bağlı gerekçe boşsa boş metin); hata fırlatırsa form hatayı gösterir. */
  readonly onSubmit: (reason: string) => Promise<void>;
  /** Gerekçe boş bırakılabilir; yazılırsa uzunluk sınırını yine `api` denetler. */
  readonly reasonOptional?: boolean;
};

export function ReasonDialog({
  id,
  icon: Icon,
  triggerLabel,
  title,
  description,
  confirmLabel,
  onSubmit,
  reasonOptional = false,
}: Props) {
  const t = useTranslations("reasonDialog");
  const errorTexts = useFormErrorTexts();
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const reasonId = `reason-${id}`;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const reason = String(new FormData(event.currentTarget).get("reason") ?? "").trim();
    if (!reasonOptional && reason.length < MIN_REASON_LENGTH) {
      setErrors({ fields: { reason: t("required") } });
      return;
    }
    setPending(true);
    setErrors(noErrors);
    try {
      await onSubmit(reason);
      setOpen(false);
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
        {triggerLabel}
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription>{description}</DialogDescription>
          </DialogHeader>
          {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
          <Field data-invalid={reasonError !== undefined}>
            <FieldLabel htmlFor={reasonId}>{reasonOptional ? t("optionalLabel") : t("label")}</FieldLabel>
            <Textarea
              id={reasonId}
              name="reason"
              maxLength={MAX_REASON_LENGTH}
              required={!reasonOptional}
              aria-invalid={reasonError !== undefined}
              aria-describedby={reasonError === undefined ? `${reasonId}-ipucu` : `${reasonId}-ipucu ${reasonId}-hata`}
            />
            <FieldDescription id={`${reasonId}-ipucu`}>{reasonOptional ? t("optionalHint") : t("hint")}</FieldDescription>
            {reasonError !== undefined && <FieldError id={`${reasonId}-hata`}>{reasonError}</FieldError>}
          </Field>
          <DialogFooter>
            <DialogClose render={<Button variant="ghost" type="button" />}>{t("cancel")}</DialogClose>
            <Button variant="destructive" type="submit" disabled={pending}>
              {pending ? t("sending") : confirmLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

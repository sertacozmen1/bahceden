"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { FormField } from "@/features/account/components/FormField";
import { FormMessage } from "@/features/account/components/FormMessage";
import { noErrors, toFormErrors, type FormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import {
  legalTypesWithTaxOffice,
  payoutLegalTypes,
  type PayoutAccountView,
  type PayoutLegalType,
} from "@/features/payout/api";
import { payoutBffPath } from "@/features/payout/bff";
import { ApiRequestError } from "@/lib/apiError";
import { postToBff } from "@/lib/bffClient";

/**
 * Satıcının ödeme bilgileri formu (seller-payout-details spec'i, D2).
 *
 * Kayıtlı IBAN ve kimlik numarası güvenlik için geri gösterilmez; kaydetmek
 * için tam değerler her seferinde yeniden girilir. Vergi dairesi yalnızca onu
 * isteyen türde görünür. Sağlama denetimleri `api`'dedir; hata ilgili alana
 * bağlanır. Sağlayıcı reddederse sayfa yenilenir ve yeni durum görünür.
 */
export function PayoutForm({ account }: { readonly account: PayoutAccountView | null }) {
  const t = useTranslations("payout.form");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [legalType, setLegalType] = useState<PayoutLegalType>(account?.legalType ?? "INDIVIDUAL");
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [pending, setPending] = useState(false);
  const [saved, setSaved] = useState(false);
  const needsTaxOffice = legalTypesWithTaxOffice.includes(legalType);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const body = {
      legalType,
      legalName: String(data.get("legalName") ?? "").trim(),
      taxId: String(data.get("taxId") ?? "").trim(),
      taxOffice: needsTaxOffice ? String(data.get("taxOffice") ?? "").trim() : null,
      iban: String(data.get("iban") ?? "").trim(),
    };
    setPending(true);
    setErrors(noErrors);
    setSaved(false);
    try {
      await postToBff<PayoutAccountView>(payoutBffPath, body, "PUT");
      setSaved(true);
      // Numaralar ekranda tutulmaz: kayıttan sonra bu iki alan boşalır, maskeli özet görünür.
      for (const name of ["taxId", "iban"]) {
        const input = form.elements.namedItem(name);
        if (input instanceof HTMLInputElement) {
          input.value = "";
        }
      }
      router.refresh();
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
      if (error instanceof ApiRequestError && error.code === "PAYOUT_ACCOUNT_REJECTED") {
        router.refresh();
      }
    } finally {
      setPending(false);
    }
  }

  const legalTypeError = errors.fields.legalType;
  return (
    <form onSubmit={handleSubmit} noValidate>
      <FieldGroup>
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        {saved && errors.general === undefined && <FormMessage tone="success">{t("saved")}</FormMessage>}
        <Field data-invalid={legalTypeError !== undefined}>
          <FieldLabel id="payout-legal-type-label">{t("legalType")}</FieldLabel>
          <RadioGroup
            aria-labelledby="payout-legal-type-label"
            className="grid gap-2 sm:grid-cols-3"
            value={legalType}
            onValueChange={(value) =>
              setLegalType(payoutLegalTypes.find((candidate) => candidate === value) ?? legalType)
            }
          >
            {payoutLegalTypes.map((option) => (
              <label
                key={option}
                className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-border bg-card px-3 py-2 text-sm font-medium has-[[data-checked]]:border-primary"
              >
                <RadioGroupItem value={option} />
                {t("legalTypeOption", { type: option })}
              </label>
            ))}
          </RadioGroup>
          {legalTypeError !== undefined && <FieldError>{legalTypeError}</FieldError>}
        </Field>
        <FormField
          id="payout-legal-name"
          name="legalName"
          label={t("legalName", { type: legalType })}
          type="text"
          autoComplete="name"
          maxLength={200}
          defaultValue={account?.legalName}
          error={errors.fields.legalName}
        />
        <FormField
          id="payout-tax-id"
          name="taxId"
          label={t("taxId", { type: legalType })}
          type="text"
          inputMode="numeric"
          autoComplete="off"
          maxLength={11}
          hint={t("taxIdHint", { type: legalType })}
          error={errors.fields.taxId}
        />
        {needsTaxOffice && (
          <FormField
            id="payout-tax-office"
            name="taxOffice"
            label={t("taxOffice")}
            type="text"
            autoComplete="off"
            maxLength={80}
            defaultValue={account?.taxOffice ?? undefined}
            error={errors.fields.taxOffice}
          />
        )}
        <FormField
          id="payout-iban"
          name="iban"
          label={t("iban")}
          type="text"
          autoComplete="off"
          maxLength={34}
          hint={t("ibanHint")}
          error={errors.fields.iban}
        />
        <Button type="submit" disabled={pending}>
          {pending ? t("saving") : t("save")}
        </Button>
      </FieldGroup>
    </form>
  );
}

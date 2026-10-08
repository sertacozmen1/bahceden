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
import type { AddressView, DeliveryProvince } from "@/features/addresses/api";
import { addressBffPaths } from "@/features/addresses/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Teslimat adresi formu (cart-and-checkout spec'i, D5). İl yalnızca teslimat
 * yapılan illerden seçilir; az sayıda il olduğu için seçenekler açıkta durur
 * (Hick). Sunucunun alan hataları ilgili alana bağlanır. Adres defteri ve
 * ödeme sayfası paylaşır (K9).
 */
type AddressFormProps = {
  readonly provinces: readonly DeliveryProvince[];
  readonly address?: AddressView;
  readonly onSaved?: (saved: AddressView) => void;
  readonly onCancel?: () => void;
};

export function AddressForm({ provinces, address, onSaved, onCancel }: AddressFormProps) {
  const t = useTranslations("addresses.form");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [province, setProvince] = useState<string>(
    address?.deliverable ? address.provinceCode : (provinces[0]?.code ?? ""),
  );
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [pending, setPending] = useState(false);
  const idPrefix = address === undefined ? "address-new" : `address-${address.id}`;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const text = (name: string) => String(data.get(name) ?? "").trim();
    const body = {
      title: text("title"),
      recipientName: text("recipientName"),
      phone: text("phone"),
      provinceCode: province,
      district: text("district"),
      addressLine: text("addressLine"),
      postalCode: text("postalCode") === "" ? null : text("postalCode"),
    };
    setPending(true);
    setErrors(noErrors);
    try {
      const saved =
        address === undefined
          ? await postToBff<AddressView>(addressBffPaths.addresses, body)
          : await postToBff<AddressView>(addressBffPaths.address(address.id), body, "PUT");
      router.refresh();
      if (saved !== undefined) {
        onSaved?.(saved);
      }
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
    } finally {
      setPending(false);
    }
  }

  const provinceError = errors.fields.provinceCode;
  return (
    <form onSubmit={handleSubmit} noValidate>
      <FieldGroup>
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        <FormField
          id={`${idPrefix}-title`}
          name="title"
          label={t("title")}
          type="text"
          autoComplete="off"
          maxLength={40}
          hint={t("titleHint")}
          defaultValue={address?.title}
          error={errors.fields.title}
        />
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField
            id={`${idPrefix}-recipient`}
            name="recipientName"
            label={t("recipientName")}
            type="text"
            autoComplete="name"
            maxLength={120}
            defaultValue={address?.recipientName}
            error={errors.fields.recipientName}
          />
          <FormField
            id={`${idPrefix}-phone`}
            name="phone"
            label={t("phone")}
            type="tel"
            autoComplete="tel"
            hint={t("phoneHint")}
            defaultValue={address?.phone}
            error={errors.fields.phone}
          />
        </div>
        <Field data-invalid={provinceError !== undefined}>
          <FieldLabel id={`${idPrefix}-province-label`}>{t("province")}</FieldLabel>
          <RadioGroup
            aria-labelledby={`${idPrefix}-province-label`}
            className="grid grid-cols-2 gap-2 sm:grid-cols-4"
            value={province}
            onValueChange={(value) => setProvince(provinces.find((candidate) => candidate.code === value)?.code ?? province)}
          >
            {provinces.map((option) => (
              <label
                key={option.code}
                className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-border bg-card px-3 py-2 text-sm font-medium has-[[data-checked]]:border-primary"
              >
                <RadioGroupItem value={option.code} />
                {option.name}
              </label>
            ))}
          </RadioGroup>
          <p className="text-sm text-muted-foreground">{t("provinceHint")}</p>
          {provinceError !== undefined && <FieldError>{provinceError}</FieldError>}
        </Field>
        <div className="grid gap-5 sm:grid-cols-[1fr_8rem]">
          <FormField
            id={`${idPrefix}-district`}
            name="district"
            label={t("district")}
            type="text"
            autoComplete="address-level2"
            maxLength={80}
            defaultValue={address?.district}
            error={errors.fields.district}
          />
          <FormField
            id={`${idPrefix}-postal`}
            name="postalCode"
            label={t("postalCode")}
            type="text"
            inputMode="numeric"
            autoComplete="postal-code"
            maxLength={5}
            defaultValue={address?.postalCode ?? undefined}
            error={errors.fields.postalCode}
          />
        </div>
        <FormField
          id={`${idPrefix}-line`}
          name="addressLine"
          label={t("addressLine")}
          type="text"
          autoComplete="street-address"
          maxLength={250}
          hint={t("addressLineHint")}
          defaultValue={address?.addressLine}
          error={errors.fields.addressLine}
        />
        <div className="flex flex-wrap gap-3">
          <Button type="submit" disabled={pending}>
            {pending ? t("saving") : t("save")}
          </Button>
          {onCancel !== undefined && (
            <Button type="button" variant="ghost" disabled={pending} onClick={onCancel}>
              {t("cancel")}
            </Button>
          )}
        </div>
      </FieldGroup>
    </form>
  );
}

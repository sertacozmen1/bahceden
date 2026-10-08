"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/features/account/components/FormField";
import { FormMessage } from "@/features/account/components/FormMessage";
import { noErrors, toFormErrors, type FormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import {
  defaultDispatchDays,
  dispatchDayOptions,
  productUnits,
  type ProductUnit,
  type SellerListingView,
} from "@/features/listings/api";
import { listingBffPaths } from "@/features/listings/bff";
import { postToBff } from "@/lib/bffClient";
import { parseMinorUnits, toPriceInput } from "@/lib/money";

/**
 * İlan bilgileri formu (product-listings spec'i "Onaylı satıcı ilan oluşturur
 * ve düzenler"). Az alan, tek ekran: ad, birim, fiyat, stok, kargoya verme
 * süresi, isteğe bağlı açıklama (Miller).
 *
 * Fiyat TL olarak yazılır ve gönderilmeden önce kuruşa çevrilir; bu girdi
 * ayrıştırmasıdır, otorite `api`'dir (ADR-006). Sunucunun alan hataları ilgili
 * alana bağlanır.
 */

const STOCK_PATTERN = /^\d+$/;

type ListingFormProps =
  | { readonly mode: "create" }
  | { readonly mode: "edit"; readonly listing: SellerListingView };

export function ListingForm(props: ListingFormProps) {
  const t = useTranslations("listings.form");
  const locale = useLocale();
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const initial = props.mode === "edit" ? props.listing : undefined;
  const [unit, setUnit] = useState<ProductUnit>(initial?.unit ?? "KILOGRAM");
  const [dispatchDays, setDispatchDays] = useState<number>(initial?.dispatchDays ?? defaultDispatchDays);
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [pending, setPending] = useState(false);
  const [saved, setSaved] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const priceAmount = parseMinorUnits(String(data.get("price") ?? ""));
    const stockText = String(data.get("stock") ?? "").trim();
    const early: Record<string, string> = {};
    if (priceAmount === undefined) {
      early.priceAmount = t("priceInvalid");
    }
    if (!STOCK_PATTERN.test(stockText)) {
      early.stockQuantity = t("stockInvalid");
    }
    if (Object.keys(early).length > 0) {
      setErrors({ general: errorTexts.formHasErrors, fields: early });
      return;
    }

    const body = {
      name: String(data.get("name") ?? "").trim(),
      description: String(data.get("description") ?? "").trim(),
      unit,
      priceAmount,
      stockQuantity: Number(stockText),
      dispatchDays,
    };
    setPending(true);
    setErrors(noErrors);
    setSaved(false);
    try {
      if (props.mode === "create") {
        const created = await postToBff<SellerListingView>(listingBffPaths.listings, body);
        if (created !== undefined) {
          router.push(`/seller/products/${created.id}`);
        }
      } else {
        await postToBff<SellerListingView>(listingBffPaths.listing(props.listing.id), body, "PUT");
        setSaved(true);
        router.refresh();
      }
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
    } finally {
      setPending(false);
    }
  }

  const unitError = errors.fields.unit;
  const dispatchError = errors.fields.dispatchDays;
  const descriptionError = errors.fields.description;
  return (
    <form onSubmit={handleSubmit} noValidate>
      <FieldGroup>
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        {saved && errors.general === undefined && <FormMessage tone="success">{t("saved")}</FormMessage>}
        <FormField
          id="listing-name"
          name="name"
          label={t("name")}
          type="text"
          autoComplete="off"
          maxLength={200}
          defaultValue={initial?.name}
          error={errors.fields.name}
        />
        <Field data-invalid={unitError !== undefined}>
          <FieldLabel id="listing-unit-label">{t("unit")}</FieldLabel>
          <RadioGroup
            aria-labelledby="listing-unit-label"
            className="grid-cols-3"
            value={unit}
            onValueChange={(value) => setUnit(productUnits.find((candidate) => candidate === value) ?? unit)}
          >
            {productUnits.map((option) => (
              <label
                key={option}
                className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-border bg-card px-3 py-2 text-sm font-medium has-[[data-checked]]:border-primary"
              >
                <RadioGroupItem value={option} />
                {t("unitOption", { unit: option })}
              </label>
            ))}
          </RadioGroup>
          {unitError !== undefined && <FieldError>{unitError}</FieldError>}
        </Field>
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField
            id="listing-price"
            name="price"
            label={t("price")}
            type="text"
            inputMode="decimal"
            autoComplete="off"
            hint={t("priceHint")}
            defaultValue={initial === undefined ? undefined : toPriceInput(initial.unitPrice.amount, locale)}
            error={errors.fields.priceAmount}
          />
          <FormField
            id="listing-stock"
            name="stock"
            label={t("stock")}
            type="text"
            inputMode="numeric"
            autoComplete="off"
            hint={t("stockHint")}
            defaultValue={initial === undefined ? undefined : String(initial.stockQuantity)}
            error={errors.fields.stockQuantity}
          />
        </div>
        <Field data-invalid={dispatchError !== undefined}>
          <FieldLabel id="listing-dispatch-label">{t("dispatchDays")}</FieldLabel>
          <RadioGroup
            aria-labelledby="listing-dispatch-label"
            aria-describedby="listing-dispatch-ipucu"
            className="grid-cols-3"
            value={String(dispatchDays)}
            onValueChange={(value) =>
              setDispatchDays(dispatchDayOptions.find((candidate) => String(candidate) === value) ?? dispatchDays)
            }
          >
            {dispatchDayOptions.map((option) => (
              <label
                key={option}
                className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-border bg-card px-3 py-2 text-sm font-medium has-[[data-checked]]:border-primary"
              >
                <RadioGroupItem value={String(option)} />
                {t("dispatchOption", { days: option })}
              </label>
            ))}
          </RadioGroup>
          <FieldDescription id="listing-dispatch-ipucu">{t("dispatchHint")}</FieldDescription>
          {dispatchError !== undefined && <FieldError>{dispatchError}</FieldError>}
        </Field>
        <Field data-invalid={descriptionError !== undefined}>
          <FieldLabel htmlFor="listing-description">{t("description")}</FieldLabel>
          <Textarea
            id="listing-description"
            name="description"
            maxLength={2000}
            defaultValue={initial?.description ?? undefined}
            aria-invalid={descriptionError !== undefined}
            aria-describedby={
              descriptionError === undefined ? "listing-description-ipucu" : "listing-description-ipucu listing-description-hata"
            }
          />
          <FieldDescription id="listing-description-ipucu">{t("descriptionHint")}</FieldDescription>
          {descriptionError !== undefined && <FieldError id="listing-description-hata">{descriptionError}</FieldError>}
        </Field>
        <Button type="submit" disabled={pending}>
          {pending ? t("saving") : props.mode === "create" ? t("create") : t("save")}
        </Button>
      </FieldGroup>
    </form>
  );
}

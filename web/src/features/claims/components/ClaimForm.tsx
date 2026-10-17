"use client";

import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormMessage } from "@/features/account/components/FormMessage";
import { noErrors, toFormErrors, type FormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import { QuantityStepper } from "@/features/cart/components/QuantityStepper";
import { MAX_CLAIM_PHOTOS, MIN_CLAIM_PHOTOS } from "@/features/claims/api";
import { claimBffPaths } from "@/features/claims/bff";
import type { OrderItemView } from "@/features/orders/api";
import { postToBff } from "@/lib/bffClient";

/**
 * Ayıplı ürün talebi formu (defect-claims spec'i, ADR-081, ADR-086). Telefonda
 * tek sayfada doldurulur: sorunlu satırlar ve adetleri, açıklama, 1–3 fotoğraf
 * (telefonda kamera açılır). Erken denetim yalnızca geri bildirimdir; otorite
 * `api`'dir ve sunucunun alan hataları ilgili alana bağlanır. Başarıda sipariş
 * sayfasına dönülür.
 */

const ACCEPTED_TYPES = ["image/jpeg", "image/png"] as const;
/** Sunucudaki sınırla aynı (bahceden.storage.max-photo-bytes): yalnızca erken uyarı için. */
const MAX_PHOTO_BYTES = 10 * 1024 * 1024;
const MIN_DESCRIPTION = 10;
const MAX_DESCRIPTION = 1000;

type ClaimFormProps = {
  readonly orderNumber: string;
  readonly sellerOrderId: number;
  readonly items: readonly OrderItemView[];
};

export function ClaimForm({ orderNumber, sellerOrderId, items }: ClaimFormProps) {
  const t = useTranslations("claims.form");
  const tOrders = useTranslations("orders");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const photoInput = useRef<HTMLInputElement>(null);
  const [quantities, setQuantities] = useState<ReadonlyMap<number, number>>(new Map());
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [pending, setPending] = useState(false);

  function toggle(item: OrderItemView, checked: boolean) {
    const next = new Map(quantities);
    if (checked) {
      next.set(item.id, 1);
    } else {
      next.delete(item.id);
    }
    setQuantities(next);
  }

  function setQuantity(item: OrderItemView, quantity: number) {
    setQuantities(new Map(quantities).set(item.id, quantity));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const description = String(new FormData(event.currentTarget).get("description") ?? "").trim();
    const photos = Array.from(photoInput.current?.files ?? []);
    const early = earlyErrors(description, photos);
    if (Object.keys(early).length > 0) {
      setErrors({ general: errorTexts.formHasErrors, fields: early });
      return;
    }
    const body = new FormData();
    body.set("description", description);
    Array.from(quantities.entries()).forEach(([orderItemId, quantity], index) => {
      body.set(`lines[${index}].orderItemId`, String(orderItemId));
      body.set(`lines[${index}].quantity`, String(quantity));
    });
    photos.forEach((photo) => body.append("photos", photo));
    setErrors(noErrors);
    setPending(true);
    try {
      await postToBff(claimBffPaths.open(orderNumber, sellerOrderId), body);
      router.push(`/account/orders/${encodeURIComponent(orderNumber)}`);
      router.refresh();
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
    } finally {
      setPending(false);
    }
  }

  function earlyErrors(description: string, photos: readonly File[]): Record<string, string> {
    const found: Record<string, string> = {};
    if (quantities.size === 0) {
      found.lines = t("errors.linesRequired");
    }
    if (description.length < MIN_DESCRIPTION || description.length > MAX_DESCRIPTION) {
      found.description = t("errors.descriptionLength");
    }
    if (photos.length < MIN_CLAIM_PHOTOS || photos.length > MAX_CLAIM_PHOTOS) {
      found.photos = t("errors.photosCount");
    } else if (photos.some((photo) => !ACCEPTED_TYPES.some((type) => type === photo.type))) {
      found.photos = t("errors.photoType");
    } else if (photos.some((photo) => photo.size > MAX_PHOTO_BYTES)) {
      found.photos = t("errors.photoTooLarge");
    }
    return found;
  }

  const lineErrors = Object.entries(errors.fields)
    .filter(([field]) => field === "lines" || field.startsWith("lines["))
    .map(([, message]) => message);
  const descriptionError = errors.fields.description;
  const photosError = errors.fields.photos;

  return (
    <form onSubmit={handleSubmit} noValidate>
      <FieldGroup>
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        <fieldset className="flex flex-col gap-3" aria-describedby={lineErrors.length > 0 ? "claim-lines-hata" : undefined}>
          <legend className="mb-2 text-sm font-medium">{t("linesLabel")}</legend>
          {items.map((item) => {
            const selected = quantities.get(item.id);
            return (
              <div
                key={item.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border px-3 py-2 has-[:checked]:border-primary"
              >
                <label className="flex min-h-11 min-w-0 flex-1 cursor-pointer items-center gap-3">
                  <input
                    type="checkbox"
                    className="size-4 accent-primary"
                    checked={selected !== undefined}
                    onChange={(event) => toggle(item, event.target.checked)}
                  />
                  <span className="flex min-w-0 flex-col">
                    <span className="font-medium break-words">{item.productName}</span>
                    <span className="text-sm text-muted-foreground tabular-nums">
                      {t("purchased", { quantity: item.quantity })} {tOrders("perUnit", { unit: item.unit })}
                    </span>
                  </span>
                </label>
                {selected !== undefined && (
                  <QuantityStepper
                    value={selected}
                    max={item.quantity}
                    label={t("lineQuantity", { name: item.productName })}
                    onChange={(quantity) => setQuantity(item, quantity)}
                  />
                )}
              </div>
            );
          })}
          {lineErrors.length > 0 && (
            <FieldError id="claim-lines-hata">
              {lineErrors.map((message) => (
                <span key={message} className="block">
                  {message}
                </span>
              ))}
            </FieldError>
          )}
        </fieldset>
        <Field data-invalid={descriptionError !== undefined}>
          <FieldLabel htmlFor="claim-description">{t("descriptionLabel")}</FieldLabel>
          <Textarea
            id="claim-description"
            name="description"
            maxLength={MAX_DESCRIPTION}
            aria-invalid={descriptionError !== undefined}
            aria-describedby={descriptionError === undefined ? "claim-description-ipucu" : "claim-description-ipucu claim-description-hata"}
          />
          <FieldDescription id="claim-description-ipucu">{t("descriptionHint")}</FieldDescription>
          {descriptionError !== undefined && <FieldError id="claim-description-hata">{descriptionError}</FieldError>}
        </Field>
        <Field data-invalid={photosError !== undefined}>
          <FieldLabel htmlFor="claim-photos">{t("photosLabel")}</FieldLabel>
          <Input
            ref={photoInput}
            id="claim-photos"
            name="photos"
            type="file"
            accept={ACCEPTED_TYPES.join(",")}
            multiple
            className="py-2 file:mr-3 file:rounded-md file:border-0 file:bg-muted file:px-2 file:text-sm file:font-medium"
            aria-invalid={photosError !== undefined}
            aria-describedby={photosError === undefined ? "claim-photos-ipucu" : "claim-photos-ipucu claim-photos-hata"}
          />
          <FieldDescription id="claim-photos-ipucu">{t("photosHint")}</FieldDescription>
          {photosError !== undefined && <FieldError id="claim-photos-hata">{photosError}</FieldError>}
        </Field>
        <Button type="submit" size="lg" disabled={pending}>
          {pending ? t("submitting") : t("submit")}
        </Button>
      </FieldGroup>
    </form>
  );
}

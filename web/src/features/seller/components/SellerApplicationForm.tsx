"use client";

import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { FormField } from "@/features/account/components/FormField";
import { FormMessage } from "@/features/account/components/FormMessage";
import { noErrors, toFormErrors, type FormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import { sellerDocumentTypes, type SellerDocumentType, type SellerSelfView } from "@/features/seller/api";
import { sellerBffPaths } from "@/features/seller/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Satıcı başvuru formu (seller-onboarding spec'i "Satıcı başvurusu",
 * add-seller-onboarding D3). Tek ekran, tek gönderim: beyan alanları ve belge
 * birlikte gider.
 *
 * İstemcideki dosya denetimi yalnızca erken geri bildirimdir: tür ve boyut
 * sunucuda içerikten yeniden doğrulanır, sunucunun hatası ilgili alana bağlanır
 * (AGENTS.md §12.3). Belge türü iki seçenekli bir radyo grubudur (Hick).
 *
 * `resubmit` kipinde form reddedilen kaydın bilgileriyle dolu gelir ve `PUT`
 * ile gönderilir; belge her seferinde yeniden seçilir.
 */

/** Sunucudaki sınırla aynı (bahceden.storage.max-document-bytes): yalnızca erken uyarı için. */
const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

const ACCEPTED_TYPES = ["application/pdf", "image/jpeg", "image/png"] as const;

type SellerApplicationFormProps = {
  readonly mode: "apply" | "resubmit";
  readonly previous?: Pick<SellerSelfView, "displayName" | "city" | "phone" | "documentType">;
};

export function SellerApplicationForm({ mode, previous }: SellerApplicationFormProps) {
  const t = useTranslations("seller.application");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [pending, setPending] = useState(false);
  const [documentType, setDocumentType] = useState<SellerDocumentType | undefined>(previous?.documentType);
  const fileInput = useRef<HTMLInputElement>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    // Dosya girdinin kendisinden okunur: seçim hangi yoldan yapılmış olursa olsun aynı sonuç.
    const file = fileInput.current?.files?.[0];
    const earlyErrors = checkLocally(file);
    if (Object.keys(earlyErrors).length > 0 || file === undefined || documentType === undefined) {
      setErrors({ general: errorTexts.formHasErrors, fields: earlyErrors });
      return;
    }
    data.set("document", file);
    data.set("documentType", documentType);

    setPending(true);
    setErrors(noErrors);
    try {
      await postToBff<SellerSelfView>(sellerBffPaths.application, data, mode === "apply" ? "POST" : "PUT");
      // Sayfa sunucuda yeniden okunur ve kaydın yeni durumunu gösterir.
      router.refresh();
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
    } finally {
      setPending(false);
    }
  }

  function checkLocally(file: File | undefined): Record<string, string> {
    const found: Record<string, string> = {};
    if (documentType === undefined) {
      found.documentType = t("errors.documentTypeRequired");
    }
    if (file === undefined || file.size === 0) {
      found.document = t("errors.documentRequired");
    } else if (file.size > MAX_DOCUMENT_BYTES) {
      found.document = t("errors.documentTooLarge");
    } else if (file.type !== "" && !ACCEPTED_TYPES.some((type) => type === file.type)) {
      found.document = t("errors.documentType");
    }
    return found;
  }

  const documentTypeError = errors.fields.documentType;
  const documentError = errors.fields.document;

  return (
    <form onSubmit={handleSubmit} noValidate>
      <FieldGroup>
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        <FormField
          id="seller-display-name"
          name="displayName"
          label={t("fields.displayName")}
          type="text"
          autoComplete="organization"
          hint={t("fields.displayNameHint")}
          maxLength={160}
          defaultValue={previous?.displayName}
          error={errors.fields.displayName}
        />
        <FormField
          id="seller-city"
          name="city"
          label={t("fields.city")}
          type="text"
          autoComplete="address-level1"
          maxLength={80}
          defaultValue={previous?.city}
          error={errors.fields.city}
        />
        <FormField
          id="seller-phone"
          name="phone"
          label={t("fields.phone")}
          type="tel"
          autoComplete="tel"
          hint={t("fields.phoneHint")}
          maxLength={20}
          defaultValue={previous?.phone}
          error={errors.fields.phone}
        />
        <Field data-invalid={documentTypeError !== undefined}>
          <FieldLabel id="seller-document-type-label">{t("fields.documentType")}</FieldLabel>
          <RadioGroup
            aria-labelledby="seller-document-type-label"
            aria-describedby={documentTypeError === undefined ? undefined : "seller-document-type-hata"}
            value={documentType ?? null}
            onValueChange={(value) => setDocumentType(sellerDocumentTypes.find((type) => type === value))}
          >
            {sellerDocumentTypes.map((type) => (
              <label
                key={type}
                className="flex min-h-11 cursor-pointer items-center gap-3 rounded-lg border border-border bg-card px-3 py-2 has-[[data-checked]]:border-primary"
              >
                <RadioGroupItem value={type} aria-invalid={documentTypeError !== undefined} />
                <span className="flex flex-col">
                  <span className="text-sm font-medium">{t("documentType", { type })}</span>
                  <span className="text-sm text-muted-foreground">{t("documentTypeHint", { type })}</span>
                </span>
              </label>
            ))}
          </RadioGroup>
          {documentTypeError !== undefined && (
            <FieldError id="seller-document-type-hata">{documentTypeError}</FieldError>
          )}
        </Field>
        <Field data-invalid={documentError !== undefined}>
          <FieldLabel htmlFor="seller-document">{t("fields.document")}</FieldLabel>
          <Input
            ref={fileInput}
            id="seller-document"
            name="document"
            type="file"
            accept={ACCEPTED_TYPES.join(",")}
            required
            className="py-2 file:mr-3 file:rounded-md file:border-0 file:bg-muted file:px-2 file:text-sm file:font-medium"
            aria-invalid={documentError !== undefined}
            aria-describedby={documentError === undefined ? "seller-document-ipucu" : "seller-document-ipucu seller-document-hata"}
          />
          <FieldDescription id="seller-document-ipucu">{t("fields.documentHint")}</FieldDescription>
          {documentError !== undefined && <FieldError id="seller-document-hata">{documentError}</FieldError>}
        </Field>
        <Button type="submit" disabled={pending}>
          {pending ? t("submitting") : mode === "apply" ? t("submit") : t("resubmit")}
        </Button>
      </FieldGroup>
    </form>
  );
}

"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { FieldGroup } from "@/components/ui/field";
import { bffPaths } from "@/features/account/bff";
import { noErrors, toFormErrors, type FormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import { postToBff } from "@/lib/bffClient";
import { FormField } from "./FormField";
import { FormMessage } from "./FormMessage";

/**
 * İki adımlı girişi açma ve kapatma formu (add-two-factor-toggle D2).
 *
 * Tek düğme durumun tersini ister; mevcut parola her iki yönde de gerekir
 * (ADR-103). Başarıda sayfa sunucudan yenilenir, yeni durum gelir ve sonucu
 * söyleyen bilgi mesajı kalır. Oturum açık kalır. Yanlış parola alanın altına,
 * diğer hatalar (doğrulanmamış e-posta, hız sınırı) formun üstüne bağlanır.
 */
export function TwoFactorForm({ enabled }: { readonly enabled: boolean }) {
  const t = useTranslations();
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [done, setDone] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const next = !enabled;
    setPending(true);
    setErrors(noErrors);
    setDone(null);
    try {
      await postToBff<never>(
        bffPaths.twoFactor,
        { enabled: next, currentPassword: String(new FormData(form).get("currentPassword") ?? "") },
        "PUT",
      );
      form.reset();
      setDone(t(next ? "account.twoFactor.enabled" : "account.twoFactor.disabled"));
      router.refresh();
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-label={t("account.twoFactor.title")}>
      <FieldGroup>
        {done !== null && <FormMessage tone="success">{done}</FormMessage>}
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        <FormField
          id="two-factor-current"
          name="currentPassword"
          label={t("account.fields.currentPassword")}
          type="password"
          autoComplete="current-password"
          hint={t("account.twoFactor.passwordHint")}
          error={errors.fields.currentPassword}
        />
        <Button type="submit" variant={enabled ? "outline" : "default"} disabled={pending}>
          {pending
            ? t("account.twoFactor.submitting")
            : t(enabled ? "account.twoFactor.disable" : "account.twoFactor.enable")}
        </Button>
      </FieldGroup>
    </form>
  );
}

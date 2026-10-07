"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Button, buttonVariants } from "@/components/ui/button";
import { FieldGroup } from "@/components/ui/field";
import { bffPaths, type BffMessageResponse } from "@/features/account/bff";
import { noErrors, toFormErrors, type FormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import { postToBff } from "@/lib/bffClient";
import { FormField } from "./FormField";
import { FormMessage } from "./FormMessage";

/**
 * Parola sıfırlama talebi formu (account-identity spec'i "Parola sıfırlama").
 *
 * Yanıt adresin kayıtlı olup olmadığını söylemez (ADR-034): başarı mesajı
 * sunucudan geldiği gibi gösterilir ve her adres için aynıdır. E-posta,
 * kayıt formundaki gibi gönderilmeden önce kırpılır.
 */
export function ForgotPasswordForm() {
  const t = useTranslations();
  const errorTexts = useFormErrorTexts();
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [pending, setPending] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    setErrors(noErrors);
    try {
      const response = await postToBff<BffMessageResponse>(bffPaths.passwordResetRequest, {
        email: String(data.get("email") ?? "").trim(),
      });
      setSuccessMessage(response?.message ?? "");
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
    } finally {
      setPending(false);
    }
  }

  if (successMessage !== null) {
    return (
      <section className="flex flex-col gap-5" aria-live="polite">
        <h2 className="text-lg font-semibold tracking-tight">{t("account.forgotPassword.successTitle")}</h2>
        <FormMessage tone="success">{successMessage}</FormMessage>
        <Link className={buttonVariants({ variant: "outline" })} href="/login">
          {t("account.forgotPassword.backToSignIn")}
        </Link>
      </section>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-label={t("account.forgotPassword.title")}>
      <FieldGroup>
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        <FormField
          id="forgot-password-email"
          name="email"
          label={t("account.fields.email")}
          type="email"
          autoComplete="email"
          error={errors.fields.email}
        />
        <Button type="submit" disabled={pending}>
          {pending ? t("account.forgotPassword.submitting") : t("account.forgotPassword.submit")}
        </Button>
      </FieldGroup>
    </form>
  );
}

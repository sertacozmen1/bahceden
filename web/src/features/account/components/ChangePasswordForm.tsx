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
 * Oturum açıkken parola değiştirme formu (add-password-change D4).
 *
 * Başarıda bu cihaz dahil bütün oturumlar kapanır ve BFF çerezleri siler;
 * kullanıcı, parolanın değiştiğini söyleyen giriş ekranına gider (sıfırlamayla
 * aynı mesaj, ADR-035). Yanlış mevcut parola ve yeni parola hataları sunucudan
 * gelir ve ilgili alanın altına bağlanır; oturum açık kalır.
 */
export function ChangePasswordForm() {
  const t = useTranslations();
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    setErrors(noErrors);
    try {
      await postToBff<never>(bffPaths.passwordChange, {
        currentPassword: String(data.get("currentPassword") ?? ""),
        newPassword: String(data.get("newPassword") ?? ""),
      });
      router.push("/login?reset=1");
      // Düzen (üst başlıktaki oturum) çerezsiz yeniden çizilir; çıkışla aynı yol.
      router.refresh();
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
      setPending(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-label={t("account.changePassword.title")}>
      <FieldGroup>
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        <FormField
          id="change-password-current"
          name="currentPassword"
          label={t("account.fields.currentPassword")}
          type="password"
          autoComplete="current-password"
          error={errors.fields.currentPassword}
        />
        <FormField
          id="change-password-new"
          name="newPassword"
          label={t("account.fields.newPassword")}
          type="password"
          autoComplete="new-password"
          hint={t("account.fields.passwordHint")}
          error={errors.fields.newPassword}
        />
        <Button type="submit" disabled={pending}>
          {pending ? t("account.changePassword.submitting") : t("account.changePassword.submit")}
        </Button>
      </FieldGroup>
    </form>
  );
}

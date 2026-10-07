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
 * Sıfırlama bağlantısıyla yeni parola formu (ADR-035).
 *
 * Başarı oturum açmaz: kullanıcı giriş ekranına yönlendirilir ve orada
 * parolasının değiştiği söylenir. Token geçersizse sunucunun mesajı formun
 * üstünde gösterilir; o mesaj yeni bağlantı istenebileceğini zaten söyler,
 * sayfanın alt notu da o yolu sunar.
 */
type ResetPasswordFormProps = {
  /** E-postadaki bağlantıdan gelen tek kullanımlık değer; sayfa boş olmadığını denetler. */
  readonly token: string;
};

export function ResetPasswordForm({ token }: ResetPasswordFormProps) {
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
      await postToBff<never>(bffPaths.passwordReset, { token, password: String(data.get("password") ?? "") });
      router.push("/login?reset=1");
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
      setPending(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-label={t("account.resetPassword.title")}>
      <FieldGroup>
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        <FormField
          id="reset-password-password"
          name="password"
          label={t("account.fields.newPassword")}
          type="password"
          autoComplete="new-password"
          hint={t("account.fields.passwordHint")}
          error={errors.fields.password}
        />
        <Button type="submit" disabled={pending}>
          {pending ? t("account.resetPassword.submitting") : t("account.resetPassword.submit")}
        </Button>
      </FieldGroup>
    </form>
  );
}

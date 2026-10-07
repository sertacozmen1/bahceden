"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { FieldGroup } from "@/components/ui/field";
import { bffPaths, type BffMessageResponse } from "@/features/account/bff";
import { noErrors, toFormErrors, type FormErrors } from "@/features/account/formErrors";
import { postToBff } from "@/lib/bffClient";
import { texts } from "@/lib/texts";
import { FormField } from "./FormField";
import { FormMessage } from "./FormMessage";

/**
 * Kayıt formu. İstemci bileşeni: gönderim durumu ve sunucu hatalarının alanlara
 * bağlanması etkileşim gerektirir.
 *
 * E-posta gönderilmeden önce kırpılır: sunucu baştaki/sondaki boşluğu
 * sessizce düzeltmez, reddeder (Faz 1 kararı); düzeltme formun işi.
 *
 * Yanıt e-postanın kayıtlı olup olmadığını söylemez (design.md D4); başarı
 * mesajı sunucudan geldiği gibi gösterilir.
 */
export function RegisterForm() {
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [pending, setPending] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    setErrors(noErrors);
    try {
      const response = await postToBff<BffMessageResponse>(bffPaths.register, {
        email: String(data.get("email") ?? "").trim(),
        password: String(data.get("password") ?? ""),
      });
      setSuccessMessage(response?.message ?? "");
    } catch (error) {
      setErrors(toFormErrors(error));
    } finally {
      setPending(false);
    }
  }

  if (successMessage !== null) {
    return (
      <section className="flex flex-col gap-5" aria-live="polite">
        <h2 className="text-lg font-semibold tracking-tight">{texts.account.register.successTitle}</h2>
        <FormMessage tone="success">{successMessage}</FormMessage>
        <Link className={buttonVariants()} href="/giris">
          {texts.account.register.goToSignIn}
        </Link>
      </section>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <FieldGroup>
        {errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>}
        <FormField
          id="kayit-eposta"
          name="email"
          label={texts.account.fields.email}
          type="email"
          autoComplete="email"
          error={errors.fields.email}
        />
        <FormField
          id="kayit-parola"
          name="password"
          label={texts.account.fields.password}
          type="password"
          autoComplete="new-password"
          hint={texts.account.fields.passwordHint}
          error={errors.fields.password}
        />
        <Button type="submit" disabled={pending}>
          {pending ? texts.account.register.submitting : texts.account.register.submit}
        </Button>
      </FieldGroup>
    </form>
  );
}

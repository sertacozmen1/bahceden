"use client";

import type { Route } from "next";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { FieldGroup } from "@/components/ui/field";
import { bffPaths, type BffLoginResponse } from "@/features/account/bff";
import { noErrors, toFormErrors, type FormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import { postToBff } from "@/lib/bffClient";
import { FormField } from "./FormField";
import { FormMessage } from "./FormMessage";

/**
 * Giriş formu, iki adımlı (ADR-017, design.md D8).
 *
 * Birinci adım e-posta + parola. Sunucu ikinci adım beklediğini bildirirse
 * aynı ekranda kod alanı açılır; e-posta tekrar sorulmaz. Hatalı kimlik
 * bilgisinde sunucunun genel mesajı gösterilir: hangi alanın yanlış olduğu
 * söylenmez (design.md D4).
 *
 * Başarıda hedefe gidilir ve sunucu bileşenleri yenilenir: düzen oturum
 * bilgisini sunucuda yeniden okur, token tarayıcıya hiç uğramaz.
 */
type LoginFormProps = {
  /** Giriş sonrası dönülecek yol; çağıran güvenli yol olduğunu doğrular. */
  readonly returnPath: Route;
};

export function LoginForm({ returnPath }: LoginFormProps) {
  const t = useTranslations();
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [errors, setErrors] = useState<FormErrors>(noErrors);
  const [pending, setPending] = useState(false);
  const [challenge, setChallenge] = useState<{ email: string; message: string } | null>(null);

  async function submit(path: string, body: Record<string, string>, email: string) {
    setPending(true);
    setErrors(noErrors);
    try {
      const response = await postToBff<BffLoginResponse>(path, body);
      if (response?.outcome === "CHALLENGE_REQUIRED") {
        setChallenge({ email, message: response.message });
        setPending(false);
        return;
      }
      router.push(returnPath);
      router.refresh();
    } catch (error) {
      setErrors(toFormErrors(error, errorTexts));
      setPending(false);
    }
  }

  function handleCredentials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const email = String(data.get("email") ?? "").trim();
    void submit(bffPaths.login, { email, password: String(data.get("password") ?? "") }, email);
  }

  function handleCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (challenge === null) {
      return;
    }
    const data = new FormData(event.currentTarget);
    void submit(bffPaths.challenge, { email: challenge.email, code: String(data.get("code") ?? "").trim() }, challenge.email);
  }

  const alert = errors.general !== undefined && <FormMessage tone="error">{errors.general}</FormMessage>;

  if (challenge !== null) {
    return (
      <form onSubmit={handleCode} noValidate aria-label={t("account.login.codeTitle")}>
        <FieldGroup>
          <h2 className="text-lg font-semibold tracking-tight">{t("account.login.codeTitle")}</h2>
          <FormMessage tone="info">{challenge.message}</FormMessage>
          {alert}
          <FormField
            id="login-code"
            name="code"
            label={t("account.fields.code")}
            type="text"
            autoComplete="one-time-code"
            inputMode="numeric"
            maxLength={6}
            hint={t("account.fields.codeHint")}
            error={errors.fields.code}
            className="font-mono text-lg tracking-[0.4em] lg:text-lg"
          />
          <div className="flex flex-col gap-2">
            <Button type="submit" disabled={pending}>
              {pending ? t("account.login.codeSubmitting") : t("account.login.codeSubmit")}
            </Button>
            <Button
              variant="ghost"
              type="button"
              onClick={() => {
                setChallenge(null);
                setErrors(noErrors);
              }}
            >
              {t("account.login.startOver")}
            </Button>
          </div>
        </FieldGroup>
      </form>
    );
  }

  return (
    <form onSubmit={handleCredentials} noValidate aria-label={t("account.login.title")}>
      <FieldGroup>
        {alert}
        <FormField
          id="login-email"
          name="email"
          label={t("account.fields.email")}
          type="email"
          autoComplete="email"
          error={errors.fields.email}
        />
        <FormField
          id="login-password"
          name="password"
          label={t("account.fields.password")}
          type="password"
          autoComplete="current-password"
          error={errors.fields.password}
        />
        <Button type="submit" disabled={pending}>
          {pending ? t("account.login.submitting") : t("account.login.submit")}
        </Button>
      </FieldGroup>
    </form>
  );
}

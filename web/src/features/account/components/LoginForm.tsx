"use client";

import type { Route } from "next";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { bffPaths, type BffLoginResponse } from "@/features/account/bff";
import { noErrors, toFormErrors, type FormErrors } from "@/features/account/formErrors";
import { postToBff } from "@/lib/bffClient";
import { texts } from "@/lib/texts";
import styles from "./AccountForm.module.css";
import { FormField } from "./FormField";

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
      setErrors(toFormErrors(error));
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

  const alert = errors.general !== undefined && (
    <p className={styles.alert} role="alert">
      {errors.general}
    </p>
  );

  if (challenge !== null) {
    return (
      <form className={styles.form} onSubmit={handleCode} noValidate aria-label={texts.account.login.codeTitle}>
        <h2>{texts.account.login.codeTitle}</h2>
        <p className={styles.notice} aria-live="polite">
          {challenge.message}
        </p>
        {alert}
        <FormField
          id="giris-kod"
          name="code"
          label={texts.account.fields.code}
          type="text"
          autoComplete="one-time-code"
          inputMode="numeric"
          maxLength={6}
          hint={texts.account.fields.codeHint}
          error={errors.fields.code}
        />
        <button className={styles.primary} type="submit" disabled={pending}>
          {pending ? texts.account.login.codeSubmitting : texts.account.login.codeSubmit}
        </button>
        <button
          className={styles.secondary}
          type="button"
          onClick={() => {
            setChallenge(null);
            setErrors(noErrors);
          }}
        >
          {texts.account.login.startOver}
        </button>
      </form>
    );
  }

  return (
    <form className={styles.form} onSubmit={handleCredentials} noValidate aria-label={texts.account.login.title}>
      {alert}
      <FormField
        id="giris-eposta"
        name="email"
        label={texts.account.fields.email}
        type="email"
        autoComplete="email"
        error={errors.fields.email}
      />
      <FormField
        id="giris-parola"
        name="password"
        label={texts.account.fields.password}
        type="password"
        autoComplete="current-password"
        error={errors.fields.password}
      />
      <button className={styles.primary} type="submit" disabled={pending}>
        {pending ? texts.account.login.submitting : texts.account.login.submit}
      </button>
    </form>
  );
}

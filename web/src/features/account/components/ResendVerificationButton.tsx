"use client";

import { useState } from "react";
import { MailPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { bffPaths, type BffMessageResponse } from "@/features/account/bff";
import { toFormErrors } from "@/features/account/formErrors";
import { postToBff } from "@/lib/bffClient";
import { texts } from "@/lib/texts";
import { FormMessage } from "./FormMessage";

/**
 * Yeni doğrulama bağlantısı isteği (design.md D9). Sonuç metinle bildirilir;
 * hız sınırı aşılırsa sunucunun mesajı olduğu gibi gösterilir.
 */
export function ResendVerificationButton() {
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null);

  async function resend() {
    setPending(true);
    try {
      const response = await postToBff<BffMessageResponse>(bffPaths.verificationRequest);
      setResult({ ok: true, message: response?.message ?? texts.account.myAccount.resent });
    } catch (error) {
      setResult({ ok: false, message: toFormErrors(error).general ?? texts.account.errors.unavailable });
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <Button variant="secondary" type="button" onClick={() => void resend()} disabled={pending}>
        <MailPlus aria-hidden />
        {pending ? texts.account.myAccount.resending : texts.account.myAccount.resend}
      </Button>
      {result !== null && <FormMessage tone={result.ok ? "success" : "error"}>{result.message}</FormMessage>}
    </div>
  );
}

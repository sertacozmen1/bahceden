"use client";

import { useState } from "react";
import { Check, Copy } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

/**
 * Gönderi kodunu panoya kopyalar (add-shipments D4): yazıcısı olmayan satıcı
 * kodu şubede söyler veya mesajla iletir. Pano yoksa (eski tarayıcı, izin)
 * kod zaten ekranda seçilebilir durumdadır; düğme yalnızca kolaylıktır.
 */
export function CopyCode({ code }: { readonly code: string }) {
  const t = useTranslations("shipments");
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Button type="button" variant="outline" onClick={() => void copy()} aria-live="polite">
      {copied ? <Check aria-hidden /> : <Copy aria-hidden />}
      {copied ? t("copied") : t("copy")}
    </Button>
  );
}

"use client";

import { CloudOff, RotateCcw } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";

/**
 * Hata durumu (AGENTS.md §12.5).
 *
 * Kullanıcıya iki şey söylenir: ne oldu ve ne yapabilir. Teknik ayrıntı
 * gösterilmez, konsola da yazılmaz (K6, K10): sunucu tarafı hatası Next.js
 * tarafından sunucu log'una düşer ve `digest` ile eşleştirilebilir.
 *
 * İstemci bileşeni olmak zorunda: `reset` ile yeniden denemeyi sunar.
 */
export default function StorefrontError({ reset }: { readonly reset: () => void }) {
  const t = useTranslations();
  return (
    <StatusPanel role="alert" icon={CloudOff} title={t("errors.title")} body={t("errors.body")}>
      <Button type="button" onClick={reset}>
        <RotateCcw aria-hidden />
        {t("errors.retry")}
      </Button>
    </StatusPanel>
  );
}

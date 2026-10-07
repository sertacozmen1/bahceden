"use client";

import { CircleAlert, RotateCcw } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";

/**
 * Hesap sayfalarının hata durumu (AGENTS.md §12.5): ne oldu ve ne yapılabilir.
 * Teknik ayrıntı gösterilmez ve konsola yazılmaz (K6, K10).
 *
 * İstemci bileşeni olmak zorunda: `reset` ile yeniden denemeyi sunar.
 */
export default function AccountError({ reset }: { readonly reset: () => void }) {
  const t = useTranslations();
  return (
    <StatusPanel role="alert" icon={CircleAlert} title={t("account.errors.title")} body={t("account.errors.body")}>
      <Button type="button" onClick={reset}>
        <RotateCcw aria-hidden />
        {t("account.errors.retry")}
      </Button>
    </StatusPanel>
  );
}

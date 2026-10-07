"use client";

import { CircleAlert, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";
import { texts } from "@/lib/texts";

/**
 * Hesap sayfalarının hata durumu (AGENTS.md §12.5): ne oldu ve ne yapılabilir.
 * Teknik ayrıntı gösterilmez ve konsola yazılmaz (K6, K10).
 *
 * İstemci bileşeni olmak zorunda: `reset` ile yeniden denemeyi sunar.
 */
export default function AccountError({ reset }: { readonly reset: () => void }) {
  return (
    <StatusPanel role="alert" icon={CircleAlert} title={texts.account.errors.title} body={texts.account.errors.body}>
      <Button type="button" onClick={reset}>
        <RotateCcw aria-hidden />
        {texts.account.errors.retry}
      </Button>
    </StatusPanel>
  );
}

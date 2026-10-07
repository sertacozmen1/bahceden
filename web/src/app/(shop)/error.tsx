"use client";

import { CloudOff, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";
import { texts } from "@/lib/texts";

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
  return (
    <StatusPanel role="alert" icon={CloudOff} title={texts.errors.title} body={texts.errors.body}>
      <Button type="button" onClick={reset}>
        <RotateCcw aria-hidden />
        {texts.errors.retry}
      </Button>
    </StatusPanel>
  );
}

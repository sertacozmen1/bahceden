import type * as React from "react";
import { cn } from "@/lib/utils";

/**
 * Çok satırlı metin girdisi (shadcn/ui base-nova `textarea`, ADR-027).
 *
 * `Input` ile aynı ölçüler: mobilde 16px yazı (iOS yakınlaştırmaz), kart
 * zemini, `aria-invalid` ile hem renk hem ekran okuyucu. Yükseklik içeriğe
 * göre büyür (`field-sizing-content`); en az iki satır.
 */
function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "flex field-sizing-content min-h-20 w-full rounded-lg border border-input bg-card px-3 py-2 text-base transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-60 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 lg:text-sm dark:bg-input/20 dark:aria-invalid:ring-destructive/40",
        className,
      )}
      {...props}
    />
  );
}

export { Textarea };

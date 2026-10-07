import type * as React from "react";
import { Input as InputPrimitive } from "@base-ui/react/input";
import { cn } from "@/lib/utils";

/**
 * Metin girdisi (shadcn/ui base-nova, ADR-027). Mobilde 44px yükseklik ve
 * 16px yazı: iOS daha küçük yazıda odaklanınca sayfayı yakınlaştırır.
 * Geçersiz durum `aria-invalid` ile hem renge hem ekran okuyucuya yansır.
 */
function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      className={cn(
        "h-11 w-full min-w-0 rounded-lg border border-input bg-card px-3 py-1 text-base transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-60 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 lg:h-10 lg:text-sm dark:bg-input/20 dark:aria-invalid:ring-destructive/40",
        className,
      )}
      {...props}
    />
  );
}

export { Input };

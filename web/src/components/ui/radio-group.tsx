"use client";

import { Radio as RadioPrimitive } from "@base-ui/react/radio";
import { RadioGroup as RadioGroupPrimitive } from "@base-ui/react/radio-group";
import { cn } from "@/lib/utils";

/**
 * Radyo grubu (shadcn/ui base-nova `radio-group`, ADR-027).
 *
 * Varsayılandan farkı: öğe yine 16px'lik bir daire, ama dokunma alanı
 * `after:` ile genişletilir. Seçenekler satır olarak etiketle sarıldığında
 * etiketin kendisi en az 44px yüksekliğindedir (`RadioGroupOption` bunu
 * kullanan ekranın işidir). `name` verildiğinde Base UI gizli bir girdi
 * üretir, yani seçim `FormData` ile birlikte gönderilir. Ok tuşlarıyla
 * gezinme ve odak yönetimi Base UI'dan gelir.
 */
function RadioGroup({ className, ...props }: RadioGroupPrimitive.Props) {
  return <RadioGroupPrimitive data-slot="radio-group" className={cn("grid w-full gap-2", className)} {...props} />;
}

function RadioGroupItem({ className, ...props }: RadioPrimitive.Root.Props) {
  return (
    <RadioPrimitive.Root
      data-slot="radio-group-item"
      className={cn(
        "peer relative flex aspect-square size-4 shrink-0 rounded-full border border-input bg-card outline-none after:absolute after:-inset-x-3 after:-inset-y-3 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-60 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 data-checked:border-primary data-checked:bg-primary data-checked:text-primary-foreground dark:bg-input/20",
        className,
      )}
      {...props}
    >
      <RadioPrimitive.Indicator data-slot="radio-group-indicator" className="flex size-4 items-center justify-center">
        <span className="absolute top-1/2 left-1/2 size-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-primary-foreground" />
      </RadioPrimitive.Indicator>
    </RadioPrimitive.Root>
  );
}

export { RadioGroup, RadioGroupItem };

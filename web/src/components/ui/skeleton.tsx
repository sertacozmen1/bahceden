import type * as React from "react";
import { cn } from "@/lib/utils";

/**
 * Yükleniyor iskeleti (shadcn/ui base-nova, ADR-027). Nabız animasyonu
 * azaltılmış hareket tercihinde durur (`motion-reduce`).
 */
function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      className={cn("animate-pulse rounded-lg bg-muted motion-reduce:animate-none", className)}
      {...props}
    />
  );
}

export { Skeleton };

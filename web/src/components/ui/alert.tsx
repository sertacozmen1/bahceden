import type * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

/**
 * Bildirim kutusu (shadcn/ui base-nova, ADR-027).
 *
 * Rolü çağıran belirler: hata `role="alert"` (varsayılan), bilgi ve başarı
 * `role="status"`. Böylece ekran okuyucu yalnızca gerçek hatada sözü keser.
 * `success` ve `info` varyantları projeye özgüdür (ADR-028): metin ön plan
 * renginde kalır, durumu ikon ve kenar rengi taşır.
 */
const alertVariants = cva(
  "group/alert relative grid w-full gap-0.5 rounded-lg border px-3.5 py-3 text-left text-sm has-[>svg]:grid-cols-[auto_1fr] has-[>svg]:gap-x-2.5 *:[svg]:row-span-2 *:[svg]:translate-y-0.5 *:[svg]:text-current *:[svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default: "bg-card text-card-foreground",
        success: "border-success/25 bg-success/8 text-foreground *:[svg]:text-success",
        info: "border-info/25 bg-info/8 text-foreground *:[svg]:text-info",
        destructive: "border-destructive/30 bg-destructive/5 text-destructive dark:bg-destructive/10",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  },
);

function Alert({
  className,
  variant,
  role = "alert",
  ...props
}: React.ComponentProps<"div"> & VariantProps<typeof alertVariants>) {
  return <div data-slot="alert" role={role} className={cn(alertVariants({ variant }), className)} {...props} />;
}

function AlertTitle({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div data-slot="alert-title" className={cn("font-medium group-has-[>svg]/alert:col-start-2", className)} {...props} />
  );
}

function AlertDescription({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="alert-description"
      className={cn("text-sm text-pretty group-has-[>svg]/alert:col-start-2", className)}
      {...props}
    />
  );
}

export { Alert, AlertTitle, AlertDescription };

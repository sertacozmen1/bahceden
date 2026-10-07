import type * as React from "react";
import { cn } from "@/lib/utils";
import { Label } from "@/components/ui/label";

/**
 * Form alanı yapı taşları (shadcn/ui base-nova `field`, ADR-027).
 *
 * Kayıttaki sürümden iki bilinçli fark:
 * - `FieldError` `role="alert"` taşımaz. Alan hatası girdiye
 *   `aria-describedby` ile bağlıdır; formun genel hata kutusu zaten tek bir
 *   uyarı olarak okunur. Her alan ayrıca uyarı verirse ekran okuyucu aynı
 *   gönderimde birden çok kez sözü keser.
 * - `errors` dizisi yerine tek mesaj alır: sunucu alan başına tek mesaj
 *   döner (§11.5) ve istemcide doğrulama kütüphanesi yok.
 * Bu yüzden istemci kancası gerekmez; dosya sunucuda da render edilebilir.
 */
function FieldGroup({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="field-group" className={cn("flex w-full flex-col gap-5", className)} {...props} />;
}

function Field({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      role="group"
      data-slot="field"
      className={cn("group/field flex w-full flex-col gap-2", className)}
      {...props}
    />
  );
}

function FieldLabel({ className, ...props }: React.ComponentProps<typeof Label>) {
  return <Label data-slot="field-label" className={cn("w-fit leading-snug", className)} {...props} />;
}

function FieldDescription({ className, ...props }: React.ComponentProps<"p">) {
  return (
    <p
      data-slot="field-description"
      className={cn("text-sm leading-normal font-normal text-muted-foreground", className)}
      {...props}
    />
  );
}

function FieldError({ className, ...props }: React.ComponentProps<"p">) {
  return <p data-slot="field-error" className={cn("text-sm font-normal text-destructive", className)} {...props} />;
}

export { Field, FieldLabel, FieldDescription, FieldError, FieldGroup };

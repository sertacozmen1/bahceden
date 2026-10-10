"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { PackageCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import { packageSizes, type PackageSize } from "@/features/orders/api";
import { orderBffPaths } from "@/features/orders/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Kargoya hazırla (shipments spec'i, add-shipments D3). Satıcı sahada
 * telefonla çalışır: üç koli boyutu ve tek birincil eylem (Hick, Fitts).
 * Varsayılan orta boydur. Başarıda sayfa sunucudan yenilenir ve gönderi kartı
 * görünür; otorite `api`'dir, sunucu hatası (telefon yok, kargo firmasına
 * ulaşılamadı) olduğu gibi gösterilir.
 */
export function PrepareShipment({ sellerOrderId }: { readonly sellerOrderId: number }) {
  const t = useTranslations("shipments.prepare");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [size, setSize] = useState<PackageSize>("MEDIUM");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  async function prepare() {
    setPending(true);
    setError(undefined);
    try {
      await postToBff(orderBffPaths.shipment(sellerOrderId), { packageSize: size });
      router.refresh();
    } catch (cause) {
      setError(toFormErrors(cause, errorTexts).general);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="flex flex-col gap-4 rounded-xl border border-border bg-card p-4">
      <header className="flex flex-col gap-1">
        <h2 className="font-semibold tracking-tight">{t("title")}</h2>
        <p className="text-sm text-pretty text-muted-foreground">{t("intro")}</p>
      </header>
      <Field>
        <FieldLabel id={`package-size-${sellerOrderId}`}>{t("sizeLabel")}</FieldLabel>
        <RadioGroup
          aria-labelledby={`package-size-${sellerOrderId}`}
          className="flex flex-col gap-2"
          value={size}
          onValueChange={(value) => setSize(packageSizes.find((candidate) => candidate === value) ?? "MEDIUM")}
        >
          {packageSizes.map((candidate) => (
            <label
              key={candidate}
              className="flex min-h-11 cursor-pointer items-center gap-3 rounded-lg border border-border bg-card px-3 py-2 text-sm has-[[data-checked]]:border-primary"
            >
              <RadioGroupItem value={candidate} />
              <span className="flex flex-col">
                <span className="font-medium">{t("size", { size: candidate })}</span>
                <span className="text-muted-foreground">{t("sizeHint", { size: candidate })}</span>
              </span>
            </label>
          ))}
        </RadioGroup>
      </Field>
      {error !== undefined && <FormMessage tone="error">{error}</FormMessage>}
      <Button type="button" size="lg" disabled={pending} onClick={() => void prepare()}>
        <PackageCheck aria-hidden />
        {pending ? t("preparing") : t("action")}
      </Button>
    </section>
  );
}

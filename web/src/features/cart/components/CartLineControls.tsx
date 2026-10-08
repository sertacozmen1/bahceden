"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import type { CartView } from "@/features/cart/api";
import { cartBffPaths, maxCartQuantity } from "@/features/cart/bff";
import { postToBff } from "@/lib/bffClient";
import { QuantityStepper } from "./QuantityStepper";

/**
 * Sepet satırının eylemleri: adet ve çıkarma (D4, D10). Her değişiklikten sonra
 * sayfa sunucudan yenilenir; tutarlar ve kargo ücreti yeniden hesaplanmış gelir.
 * Satın alınamayan satırda yalnızca çıkarma vardır.
 */
type CartLineControlsProps = {
  readonly productId: number;
  readonly productName: string;
  readonly quantity: number;
  readonly stock: number;
  readonly available: boolean;
};

export function CartLineControls({ productId, productName, quantity, stock, available }: CartLineControlsProps) {
  const t = useTranslations("cart.line");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  async function run(action: () => Promise<CartView | undefined>) {
    setPending(true);
    setError(undefined);
    try {
      await action();
      router.refresh();
    } catch (cause) {
      const errors = toFormErrors(cause, errorTexts);
      setError(errors.fields.quantity ?? errors.general);
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-2">
      <div className="flex items-center gap-2">
        {available && (
          <QuantityStepper
            value={quantity}
            max={Math.min(stock, maxCartQuantity)}
            label={t("quantityOf", { name: productName })}
            disabled={pending}
            onChange={(next) => run(() => postToBff<CartView>(cartBffPaths.item(productId), { quantity: next }, "PUT"))}
          />
        )}
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t("remove", { name: productName })}
          disabled={pending}
          onClick={() => run(() => postToBff<CartView>(cartBffPaths.item(productId), undefined, "DELETE"))}
        >
          <Trash2 aria-hidden />
        </Button>
      </div>
      {error !== undefined && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

"use client";

import { Minus, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

/**
 * Adet seçici: eksi, sayı, artı (add-cart-and-checkout D10). Ürün detayı ve
 * sepet satırı paylaşır (K9). Dokunma hedefi 44px (AGENTS.md §12.4); sınırlar
 * erken geri bildirimdir, otorite `api`'dir. Ayıplı ürün teklifinde satır 0
 * olabildiği için alt sınır verilebilir; varsayılan 1'dir (add-defect-claims).
 */
type QuantityStepperProps = {
  readonly value: number;
  readonly min?: number;
  readonly max: number;
  readonly label: string;
  readonly disabled?: boolean;
  readonly onChange: (next: number) => void;
};

export function QuantityStepper({ value, min = 1, max, label, disabled = false, onChange }: QuantityStepperProps) {
  const t = useTranslations("cart.quantity");
  return (
    <div role="group" aria-label={label} className="inline-flex items-center rounded-lg border border-border">
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label={t("decrease")}
        disabled={disabled || value <= min}
        onClick={() => onChange(value - 1)}
      >
        <Minus aria-hidden />
      </Button>
      <output aria-live="polite" className="min-w-10 text-center font-medium tabular-nums">
        {value}
      </output>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label={t("increase")}
        disabled={disabled || value >= max}
        onClick={() => onChange(value + 1)}
      >
        <Plus aria-hidden />
      </Button>
    </div>
  );
}

"use client";

import { useState } from "react";
import { CreditCard, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import type { AddressView, DeliveryProvince } from "@/features/addresses/api";
import { AddressForm } from "@/features/addresses/components/AddressForm";
import type { OrderPlaced } from "@/features/orders/api";
import { idempotencyHeader, orderBffPaths } from "@/features/orders/bff";
import { postToBff } from "@/lib/bffClient";

/**
 * Ödeme adımı (cart-and-checkout spec'i, D6, D10): teslimat adresini seç veya
 * ekle, ödemeye geç.
 *
 * Tekrar anahtarı ekran açıldığında bir kez üretilir ve bu ekrandaki bütün
 * denemelerde korunur: çift tıklama veya ağ hatasından sonra yeniden deneme
 * ikinci sipariş açmaz (AGENTS.md §11.5). Ödeme sayfası sağlayıcının adresi
 * olabileceği için tam sayfa yönlendirme yapılır.
 */
type CheckoutFormProps = {
  readonly addresses: readonly AddressView[];
  readonly provinces: readonly DeliveryProvince[];
  readonly navigate?: (url: string) => void;
};

export function CheckoutForm({ addresses, provinces, navigate = (url) => window.location.assign(url) }: CheckoutFormProps) {
  const t = useTranslations("checkout");
  const errorTexts = useFormErrorTexts();
  const deliverable = addresses.filter((address) => address.deliverable);
  const [addressId, setAddressId] = useState<number | undefined>(deliverable[0]?.id);
  const [adding, setAdding] = useState(deliverable.length === 0);
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  async function pay() {
    if (addressId === undefined) {
      setError(t("addressRequired"));
      return;
    }
    setPending(true);
    setError(undefined);
    try {
      const placed = await postToBff<OrderPlaced>(orderBffPaths.orders, { addressId }, "POST", {
        [idempotencyHeader]: idempotencyKey,
      });
      if (placed !== undefined) {
        navigate(placed.paymentRedirectUrl);
        return;
      }
    } catch (cause) {
      setError(toFormErrors(cause, errorTexts).general);
    }
    setPending(false);
  }

  return (
    <div className="flex flex-col gap-5">
      <Field>
        <FieldLabel id="checkout-address-label">{t("address")}</FieldLabel>
        {deliverable.length > 0 && (
          <RadioGroup
            aria-labelledby="checkout-address-label"
            className="flex flex-col gap-2"
            value={addressId === undefined ? "" : String(addressId)}
            onValueChange={(value) => setAddressId(deliverable.find((address) => String(address.id) === value)?.id)}
          >
            {deliverable.map((address) => (
              <label
                key={address.id}
                className="flex min-h-11 cursor-pointer items-start gap-3 rounded-lg border border-border bg-card px-3 py-3 text-sm has-[[data-checked]]:border-primary"
              >
                <RadioGroupItem value={String(address.id)} className="mt-0.5" />
                <span className="flex flex-col gap-0.5 break-words">
                  <span className="font-medium">{address.title}</span>
                  <span>
                    {address.recipientName} · {address.phone}
                  </span>
                  <span className="text-muted-foreground">
                    {address.addressLine}, {address.district} / {address.provinceName}
                  </span>
                </span>
              </label>
            ))}
          </RadioGroup>
        )}
        {addresses.length > deliverable.length && (
          <p className="text-sm text-muted-foreground">{t("someNotDeliverable")}</p>
        )}
      </Field>
      {adding ? (
        <div className="flex flex-col gap-3 rounded-lg border border-border p-4">
          <h3 className="font-semibold tracking-tight">{t("newAddress")}</h3>
          <AddressForm
            provinces={provinces}
            onSaved={(saved) => {
              setAddressId(saved.id);
              setAdding(false);
            }}
            onCancel={deliverable.length === 0 ? undefined : () => setAdding(false)}
          />
        </div>
      ) : (
        <Button type="button" variant="outline" className="self-start" onClick={() => setAdding(true)}>
          <Plus aria-hidden />
          {t("addAddress")}
        </Button>
      )}
      {error !== undefined && <FormMessage tone="error">{error}</FormMessage>}
      <Button type="button" size="lg" disabled={pending || adding} onClick={pay}>
        <CreditCard aria-hidden />
        {pending ? t("paying") : t("pay")}
      </Button>
    </div>
  );
}

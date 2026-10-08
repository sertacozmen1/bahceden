"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ShoppingBasket } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button, buttonVariants } from "@/components/ui/button";
import { FormMessage } from "@/features/account/components/FormMessage";
import { toFormErrors, useFormErrorTexts } from "@/features/account/formErrors";
import type { CartView } from "@/features/cart/api";
import { cartBffPaths, maxCartQuantity } from "@/features/cart/bff";
import { postToBff } from "@/lib/bffClient";
import { QuantityStepper } from "./QuantityStepper";

/**
 * Ürün detayında adet seçimi ve "Sepete ekle" (cart-and-checkout spec'i, D10).
 * Ürün sepette varsa adedi seçilen değer olur. Başarıda sayfa yenilenir,
 * böylece başlıktaki sepet sayısı sunucudan güncellenir; istemcide sepet
 * durumu tutulmaz.
 */
export function AddToCart({ productId, stock }: { readonly productId: number; readonly stock: number }) {
  const t = useTranslations("cart.add");
  const errorTexts = useFormErrorTexts();
  const router = useRouter();
  const max = Math.min(stock, maxCartQuantity);
  const [quantity, setQuantity] = useState(1);
  const [pending, setPending] = useState(false);
  const [added, setAdded] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  async function add() {
    setPending(true);
    setAdded(false);
    setError(undefined);
    try {
      await postToBff<CartView>(cartBffPaths.item(productId), { quantity }, "PUT");
      setAdded(true);
      router.refresh();
    } catch (cause) {
      const errors = toFormErrors(cause, errorTexts);
      setError(errors.fields.quantity ?? errors.general);
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <QuantityStepper value={quantity} max={max} label={t("quantity")} disabled={pending} onChange={setQuantity} />
        <Button type="button" size="lg" className="grow sm:grow-0" disabled={pending} onClick={add}>
          <ShoppingBasket aria-hidden />
          {pending ? t("adding") : t("action")}
        </Button>
      </div>
      {error !== undefined && <FormMessage tone="error">{error}</FormMessage>}
      {added && (
        <FormMessage tone="success">
          {t("added")}{" "}
          <Link className="font-medium underline underline-offset-4" href="/cart">
            {t("goToCart")}
          </Link>
        </FormMessage>
      )}
    </div>
  );
}

/** Oturumsuz alıcıya: giriş yaptıktan sonra aynı ürüne döner. */
export function SignInToBuy({ slug }: { readonly slug: string }) {
  const t = useTranslations("cart.add");
  return (
    <Link
      className={buttonVariants({ size: "lg", className: "self-start" })}
      href={`/login?next=${encodeURIComponent(`/products/${slug}`)}`}
    >
      <ShoppingBasket aria-hidden />
      {t("signIn")}
    </Link>
  );
}

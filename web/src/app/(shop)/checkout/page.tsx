import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { FormMessage } from "@/features/account/components/FormMessage";
import { requireSignedIn } from "@/features/account/requireSession";
import { listAddresses, listDeliveryProvinces } from "@/features/addresses/api";
import { getCart } from "@/features/cart/api";
import { CartGroups } from "@/features/cart/components/CartGroups";
import { CartTotals } from "@/features/cart/components/CartTotals";
import { CheckoutForm } from "@/features/orders/components/CheckoutForm";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("checkout.title") };
}

/**
 * Ödeme adımı (cart-and-checkout spec'i, D10): adres, satıcılara göre özet,
 * toplam ve cayma bilgisi (ADR-056 §3), ardından ödeme. Boş sepet sepete
 * döner; satın alınamayan satır varsa önce sepet düzeltilir.
 */
export default async function CheckoutPage() {
  const t = await getTranslations();
  const { accessToken } = await requireSignedIn("/checkout");
  const [cart, addresses, provinces] = await Promise.all([
    getCart(accessToken),
    listAddresses(accessToken),
    listDeliveryProvinces(),
  ]);
  if (cart.groups.length === 0) {
    redirect("/cart");
  }

  return (
    <main className="mx-auto w-full max-w-5xl px-4 pt-8 pb-12 lg:pt-12">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href="/cart">
        <ArrowLeft aria-hidden />
        {t("checkout.backToCart")}
      </Link>
      <h1 className="mb-6 text-2xl font-semibold tracking-tight lg:text-3xl">{t("checkout.title")}</h1>
      <div className="grid gap-6 lg:grid-cols-[1fr_22rem] lg:items-start">
        <section aria-labelledby="checkout-summary-title" className="flex flex-col gap-4">
          <h2 id="checkout-summary-title" className="text-lg font-semibold tracking-tight">
            {t("checkout.summaryTitle")}
          </h2>
          <CartGroups groups={cart.groups} editable={false} />
        </section>
        <Card className="lg:sticky lg:top-6">
          <CardContent className="flex flex-col gap-5">
            <CartTotals cart={cart} />
            <p className="rounded-lg bg-muted px-3 py-2 text-sm text-pretty text-muted-foreground">
              {t("checkout.withdrawalNotice")}
            </p>
            {cart.hasUnavailableItems ? (
              <>
                <FormMessage tone="error">{t("cart.hasUnavailable")}</FormMessage>
                <Link className={buttonVariants({ variant: "outline" })} href="/cart">
                  {t("checkout.backToCart")}
                </Link>
              </>
            ) : (
              <CheckoutForm addresses={addresses} provinces={provinces} />
            )}
          </CardContent>
        </Card>
      </div>
    </main>
  );
}

import type { Metadata } from "next";
import Link from "next/link";
import { ShoppingBasket } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { FormMessage } from "@/features/account/components/FormMessage";
import { requireSignedIn } from "@/features/account/requireSession";
import { getCart } from "@/features/cart/api";
import { CartGroups } from "@/features/cart/components/CartGroups";
import { CartTotals } from "@/features/cart/components/CartTotals";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("cart.title") };
}

/**
 * Sepet (cart-and-checkout spec'i, add-cart-and-checkout D4, D10): satıcılara
 * göre gruplu satırlar, satıcı başına kargo ücreti ve genel toplam. Tutarların
 * tamamı sunucudan gelir. Satın alınamaz hale gelen satır işaretlenir ve
 * toplama girmez; alıcı onu çıkarır.
 */
export default async function CartPage() {
  const t = await getTranslations();
  const { accessToken } = await requireSignedIn("/cart");
  const cart = await getCart(accessToken);

  if (cart.groups.length === 0) {
    return (
      <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
        <h1 className="mb-6 text-2xl font-semibold tracking-tight lg:text-3xl">{t("cart.title")}</h1>
        <section
          aria-labelledby="cart-empty-title"
          className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center"
        >
          <span className="grid size-12 place-items-center rounded-full bg-secondary text-primary">
            <ShoppingBasket aria-hidden className="size-6" />
          </span>
          <h2 id="cart-empty-title" className="text-lg font-semibold tracking-tight">
            {t("cart.emptyTitle")}
          </h2>
          <p className="max-w-sm text-pretty text-muted-foreground">{t("cart.emptyBody")}</p>
          <Link className={buttonVariants()} href="/">
            {t("cart.backToStore")}
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-5xl px-4 pt-8 pb-12 lg:pt-12">
      <h1 className="mb-6 text-2xl font-semibold tracking-tight lg:text-3xl">{t("cart.title")}</h1>
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem] lg:items-start">
        <CartGroups groups={cart.groups} editable />
        <Card className="lg:sticky lg:top-6">
          <CardContent className="flex flex-col gap-4">
            <h2 className="text-lg font-semibold tracking-tight">{t("cart.summaryTitle")}</h2>
            <CartTotals cart={cart} />
            {cart.hasUnavailableItems ? (
              <FormMessage tone="error">{t("cart.hasUnavailable")}</FormMessage>
            ) : (
              <Link className={buttonVariants({ size: "lg" })} href="/checkout">
                {t("cart.checkout")}
              </Link>
            )}
          </CardContent>
        </Card>
      </div>
    </main>
  );
}

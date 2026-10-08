import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, MapPin, Truck } from "lucide-react";
import { getLocale, getTranslations } from "next-intl/server";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { currentSession } from "@/features/account/api";
import { AddToCart, SignInToBuy } from "@/features/cart/components/AddToCart";
import { fetchProduct } from "@/features/catalog/api";
import { ProductGallery } from "@/features/catalog/components/ProductGallery";
import { formatMoney } from "@/lib/money";

type Params = { readonly params: Promise<{ slug: string }> };

/** Başlık ürün adıdır (kullanıcı verisi, çevrilmez); açıklama varsa özet olur. */
export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const product = await fetchProduct((await params).slug);
  const t = await getTranslations();
  if (product === null) {
    return { title: t("storefront.detail.notFoundTitle") };
  }
  return { title: product.name, description: product.description ?? undefined };
}

const LOW_STOCK_THRESHOLD = 10;

/**
 * Ürün detayı (storefront-catalog "Ürün detayı", add-product-listings D6).
 * Server Component: veri sunucuda çekilir, sayfa arama motorunda bulunur.
 * Vitrinde görünmeyen ürün `not-found.tsx`'e düşer. Fotoğrafı olmayan eski
 * ürünlerde galeri çizilmez; yer tutucu görsel yoktur. Satın alınamayan ürün
 * nedenini söyler: tükendi (stok rozeti) ya da satıcı henüz satışa hazır değil
 * (add-cart-and-checkout D1).
 */
export default async function ProductPage({ params }: Params) {
  const product = await fetchProduct((await params).slug);
  if (product === null) {
    notFound();
  }
  const t = await getTranslations();
  const locale = await getLocale();
  const signedIn = await isSignedIn();
  const stock =
    product.stockQuantity === 0
      ? { variant: "destructive" as const, label: t("storefront.outOfStock") }
      : product.stockQuantity <= LOW_STOCK_THRESHOLD
        ? { variant: "warning" as const, label: t("storefront.stockLeft", { count: product.stockQuantity }) }
        : { variant: "success" as const, label: t("storefront.inStock") };

  return (
    <main className="mx-auto w-full max-w-5xl px-4 pt-6 pb-12 lg:px-6 lg:pt-10">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href="/">
        <ArrowLeft aria-hidden />
        {t("storefront.detail.back")}
      </Link>
      <div className="grid gap-6 lg:grid-cols-2 lg:gap-10">
        <ProductGallery photos={product.photos} name={product.name} />
        <div className="flex flex-col gap-5">
          <header className="flex flex-col gap-2">
            <h1 className="text-3xl font-semibold tracking-tight break-words lg:text-4xl">{product.name}</h1>
            <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
              <span className="font-medium">{product.seller.displayName}</span>
              <span className="inline-flex items-center gap-1 text-muted-foreground">
                <MapPin aria-hidden className="size-3.5" />
                {t("storefront.sellerFrom", { city: product.seller.city })}
              </span>
            </p>
          </header>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <p className="flex items-baseline gap-1">
              <span className="text-3xl font-semibold tracking-tight tabular-nums">
                {formatMoney(product.unitPrice, locale)}
              </span>
              <span className="text-muted-foreground">{t("storefront.perUnit", { unit: product.unit })}</span>
            </p>
            <Badge variant={stock.variant}>{stock.label}</Badge>
          </div>
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <Truck aria-hidden className="size-4" />
            {t("storefront.detail.dispatch", { days: product.dispatchDays })}
          </p>
          {!product.purchasable && product.stockQuantity > 0 && (
            <p className="rounded-lg border border-border bg-muted px-3 py-2 text-sm text-muted-foreground">
              {t("storefront.detail.notReady")}
            </p>
          )}
          {product.purchasable &&
            (signedIn ? <AddToCart productId={product.id} stock={product.stockQuantity} /> : <SignInToBuy slug={product.slug} />)}
          <section aria-labelledby="description-title" className="flex flex-col gap-2">
            <h2 id="description-title" className="text-base font-semibold tracking-tight">
              {t("storefront.detail.description")}
            </h2>
            <p className="whitespace-pre-line text-pretty text-muted-foreground">
              {product.description ?? t("storefront.detail.noDescription")}
            </p>
          </section>
        </div>
      </div>
    </main>
  );
}

/** Sepete ekleme oturum ister (D4); oturum okunamazsa ürün yine gösterilir, eylem girişe yönlendirir. */
async function isSignedIn(): Promise<boolean> {
  try {
    return (await currentSession()).kind === "signed-in";
  } catch {
    return false;
  }
}

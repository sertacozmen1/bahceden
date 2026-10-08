import Image from "next/image";
import Link from "next/link";
import { MapPin, Sprout } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ProductSummary } from "@/features/catalog/api";
import { formatMoney } from "@/lib/money";
import { cardSize, photoUrl } from "@/lib/productPhotos";

/**
 * Vitrin ürün listesi.
 *
 * Sunum bileşeni: veri çekmez, yalnızca aldığı listeyi gösterir. Böylece hem
 * sunucu tarafında render edilebilir hem de testte veri uydurmadan doğrulanabilir.
 *
 * Boş durum burada ele alınır: boş beyaz sayfa yasaktır (AGENTS.md §12.5) ve
 * kullanıcı ne olduğunu, ne beklemesi gerektiğini okur.
 *
 * Kapak fotoğrafı varsa kartın üstünde durur (add-product-listings D5, D6).
 * Fotoğrafı olmayan eski ürünlerde sahte fotoğraf veya yer tutucu kutu yoktur:
 * kart tipografiye ve fiyata yaslanır. Kartın tamamı detay sayfasına götürür;
 * bağlantı başlıktadır ve kartı kaplar (erişilebilir ad ürün adıdır).
 *
 * Metinler ve para biçimi seçili dilde (ADR-029); ürün ve satıcı adı kullanıcı
 * verisidir, çevrilmez.
 */

const LOW_STOCK_THRESHOLD = 10;

export function ProductList({ products }: { readonly products: readonly ProductSummary[] }) {
  const t = useTranslations("storefront");
  const locale = useLocale();
  if (products.length === 0) {
    return (
      <section
        className="flex flex-col items-center gap-3 rounded-xl border border-dashed px-6 py-14 text-center"
        aria-labelledby="empty-title"
      >
        <span className="grid size-12 place-items-center rounded-full bg-secondary text-primary">
          <Sprout aria-hidden className="size-6" />
        </span>
        <h2 id="empty-title" className="text-lg font-semibold tracking-tight">
          {t("emptyTitle")}
        </h2>
        <p className="max-w-sm text-pretty text-muted-foreground">{t("emptyBody")}</p>
      </section>
    );
  }

  return (
    <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 lg:gap-5">
      {products.map((product) => (
        <li key={product.slug}>
          <article className="relative flex h-full">
            <Card className="w-full overflow-hidden transition-colors hover:bg-muted/40">
              {product.coverPhoto !== undefined && product.coverPhoto !== null && (
                <Image
                  src={photoUrl(product.coverPhoto.cardKey)}
                  alt={product.name}
                  width={cardSize(product.coverPhoto).width}
                  height={cardSize(product.coverPhoto).height}
                  unoptimized
                  className="-mt-(--card-spacing) aspect-[4/3] h-auto w-full object-cover"
                />
              )}
              <CardHeader>
                <CardTitle>
                  <h2 className="text-lg leading-snug">
                    <Link
                      href={`/products/${product.slug}`}
                      className="outline-none after:absolute after:inset-0 after:rounded-xl focus-visible:after:ring-3 focus-visible:after:ring-ring/50"
                    >
                      {product.name}
                    </Link>
                  </h2>
                </CardTitle>
                <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                  <span className="font-medium">{product.seller.displayName}</span>
                  <span className="inline-flex items-center gap-1 text-muted-foreground">
                    <MapPin aria-hidden className="size-3.5" />
                    {t("sellerFrom", { city: product.seller.city })}
                  </span>
                </p>
              </CardHeader>
              <CardContent className="mt-auto flex items-end justify-between gap-3">
                <p className="flex items-baseline gap-1">
                  <span className="text-2xl font-semibold tracking-tight tabular-nums">
                    {formatMoney(product.unitPrice, locale)}
                  </span>
                  <span className="text-sm text-muted-foreground">
                    {t("perUnit", { unit: product.unit ?? "other" })}
                  </span>
                </p>
                <StockBadge quantity={product.stockQuantity} />
              </CardContent>
            </Card>
          </article>
        </li>
      ))}
    </ul>
  );
}

/** Stok durumu renkle değil metinle de anlatılır (erişilebilirlik, §12.4). */
function StockBadge({ quantity }: { readonly quantity: number }) {
  const t = useTranslations("storefront");
  if (quantity === 0) {
    return <Badge variant="destructive">{t("outOfStock")}</Badge>;
  }
  if (quantity <= LOW_STOCK_THRESHOLD) {
    return <Badge variant="warning">{t("stockLeft", { count: quantity })}</Badge>;
  }
  return <Badge variant="success">{t("inStock")}</Badge>;
}

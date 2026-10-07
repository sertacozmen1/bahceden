import { MapPin, Sprout } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ProductSummary } from "@/features/catalog/api";
import { formatMoney } from "@/lib/money";
import { texts } from "@/lib/texts";

/**
 * Vitrin ürün listesi.
 *
 * Sunum bileşeni: veri çekmez, yalnızca aldığı listeyi gösterir. Böylece hem
 * sunucu tarafında render edilebilir hem de testte veri uydurmadan doğrulanabilir.
 *
 * Boş durum burada ele alınır: boş beyaz sayfa yasaktır (AGENTS.md §12.5) ve
 * kullanıcı ne olduğunu, ne beklemesi gerektiğini okur.
 *
 * Ürün görseli bilinçli olarak yok (KALANLAR B3): sahte fotoğraf veya yer
 * tutucu kutu koymak yerine kart tipografiye ve fiyata yaslanır. Fotoğraf
 * kararı gelince kartın üstüne `next/image` eklenecek yer hazır.
 */

const LOW_STOCK_THRESHOLD = 10;

export function ProductList({ products }: { readonly products: readonly ProductSummary[] }) {
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
          {texts.storefront.emptyTitle}
        </h2>
        <p className="max-w-sm text-pretty text-muted-foreground">{texts.storefront.emptyBody}</p>
      </section>
    );
  }

  return (
    <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 lg:gap-5">
      {products.map((product) => (
        <li key={product.slug}>
          {/* Ürün detay sayfası yok: kart tıklanabilir görünmez (hover yok), olmayan yere götürmez. */}
          <article className="flex h-full">
            <Card className="w-full">
              <CardHeader>
                <CardTitle>
                  <h2 className="text-lg leading-snug">{product.name}</h2>
                </CardTitle>
                <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                  <span className="font-medium">{product.seller.displayName}</span>
                  <span className="inline-flex items-center gap-1 text-muted-foreground">
                    <MapPin aria-hidden className="size-3.5" />
                    {texts.storefront.sellerFrom(product.seller.city)}
                  </span>
                </p>
              </CardHeader>
              <CardContent className="mt-auto flex items-end justify-between gap-3">
                <p className="flex items-baseline gap-1">
                  <span className="text-2xl font-semibold tracking-tight tabular-nums">
                    {formatMoney(product.unitPrice)}
                  </span>
                  <span className="text-sm text-muted-foreground">{texts.storefront.perUnit(product.unit)}</span>
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
  if (quantity === 0) {
    return <Badge variant="destructive">{texts.storefront.outOfStock}</Badge>;
  }
  if (quantity <= LOW_STOCK_THRESHOLD) {
    return <Badge variant="warning">{texts.storefront.stockLeft(quantity)}</Badge>;
  }
  return <Badge variant="secondary">{texts.storefront.inStock}</Badge>;
}

import type { ProductSummary } from "@/features/catalog/api";
import { formatMoney } from "@/lib/money";
import { texts } from "@/lib/texts";
import styles from "./ProductList.module.css";

/**
 * Vitrin ürün listesi.
 *
 * Sunum bileşeni: veri çekmez, yalnızca aldığı listeyi gösterir. Böylece hem
 * sunucu tarafında render edilebilir hem de testte veri uydurmadan doğrulanabilir.
 *
 * Boş durum burada ele alınır: boş beyaz sayfa yasaktır (AGENTS.md §12.5) ve
 * kullanıcı ne olduğunu, ne beklemesi gerektiğini okur.
 *
 * Ürün görseli bilinçli olarak yok (KALANLAR B3): yer tutucu gri kutular
 * koymak yerine tipografiye yaslanıyoruz.
 */

const LOW_STOCK_THRESHOLD = 10;

export function ProductList({ products }: { readonly products: readonly ProductSummary[] }) {
  if (products.length === 0) {
    return (
      <section className={styles.empty} aria-labelledby="empty-title">
        <h2 id="empty-title" className={styles.emptyTitle}>
          {texts.storefront.emptyTitle}
        </h2>
        <p className={styles.emptyBody}>{texts.storefront.emptyBody}</p>
      </section>
    );
  }

  return (
    <ul className={styles.list}>
      {products.map((product) => (
        <li key={product.slug} className={styles.card}>
          <article>
            <h2 className={styles.name}>{product.name}</h2>
            <p className={styles.seller}>
              <span className={styles.sellerName}>{product.seller.displayName}</span>
              <span className={styles.sellerCity}>
                {texts.storefront.sellerFrom(product.seller.city)}
              </span>
            </p>
            <p className={styles.price}>
              <span className={styles.priceAmount}>{formatMoney(product.unitPrice)}</span>
              <span className={styles.priceUnit}>{texts.storefront.perUnit(product.unit)}</span>
            </p>
            <p className={stockClassName(product.stockQuantity)}>{stockLabel(product.stockQuantity)}</p>
          </article>
        </li>
      ))}
    </ul>
  );
}

/**
 * Tanımlı sınıf adlarını birleştirir.
 *
 * CSS Modules tipleri `string | undefined` döner (tsconfig
 * `noUncheckedIndexedAccess`); koşullu sınıfları elle birleştirmek yerine
 * tanımsızları süzen tek bir yardımcı kullanılır.
 */
function cx(...names: ReadonlyArray<string | undefined>): string {
  return names.filter((name): name is string => Boolean(name)).join(" ");
}

/** Stok durumu renkle değil metinle de anlatılır (erişilebilirlik, §12.4). */
function stockLabel(quantity: number): string {
  if (quantity === 0) {
    return texts.storefront.outOfStock;
  }
  if (quantity <= LOW_STOCK_THRESHOLD) {
    return texts.storefront.stockLeft(quantity);
  }
  return texts.storefront.inStock;
}

function stockClassName(quantity: number): string {
  if (quantity === 0) {
    return cx(styles.stock, styles.stockNone);
  }
  if (quantity <= LOW_STOCK_THRESHOLD) {
    return cx(styles.stock, styles.stockLow);
  }
  return cx(styles.stock);
}

import { useLocale, useTranslations } from "next-intl";
import type { CartView } from "@/features/cart/api";
import { formatMoney } from "@/lib/money";

/**
 * Sepetin ve ödeme özetinin toplamları (D10): ürünler, kargo, genel toplam.
 * Hepsi sunucudan gelir; istemci toplama yapmaz (AGENTS.md §12.1).
 */
export function CartTotals({ cart }: { readonly cart: CartView }) {
  const t = useTranslations("cart.totals");
  const locale = useLocale();
  return (
    <dl className="flex flex-col gap-2 text-sm">
      <div className="flex justify-between gap-3">
        <dt className="text-muted-foreground">{t("items", { count: cart.itemCount })}</dt>
        <dd className="tabular-nums">{formatMoney(cart.itemsTotal, locale)}</dd>
      </div>
      <div className="flex justify-between gap-3">
        <dt className="text-muted-foreground">{t("shipping")}</dt>
        <dd className="tabular-nums">{formatMoney(cart.shippingTotal, locale)}</dd>
      </div>
      <div className="flex justify-between gap-3 border-t border-border pt-2 text-base font-semibold">
        <dt>{t("grandTotal")}</dt>
        <dd className="tabular-nums">{formatMoney(cart.grandTotal, locale)}</dd>
      </div>
    </dl>
  );
}

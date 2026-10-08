import Image from "next/image";
import Link from "next/link";
import { MapPin, Truck } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import type { CartSellerGroup } from "@/features/cart/api";
import { formatMoney } from "@/lib/money";
import { cardSize, photoUrl } from "@/lib/productPhotos";
import { CartLineControls } from "./CartLineControls";

/**
 * Sepetin satıcı grupları (cart-and-checkout spec'i, D10): her satıcı ayrı
 * kart, ayrı kargo ücreti. Sepette düzenlenebilir, ödeme özetinde salt
 * okunur (K9). Tutarlar sunucudan gelir; burada yalnızca biçimlenir.
 */
type CartGroupsProps = {
  readonly groups: readonly CartSellerGroup[];
  readonly editable: boolean;
};

export function CartGroups({ groups, editable }: CartGroupsProps) {
  const t = useTranslations("cart");
  const locale = useLocale();
  return (
    <ul className="flex flex-col gap-4">
      {groups.map((group) => (
        <li key={group.seller.slug}>
          <Card>
            <CardContent className="flex flex-col gap-4">
              <header className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                <h2 className="font-semibold tracking-tight break-words">{group.seller.displayName}</h2>
                <span className="inline-flex items-center gap-1 text-sm text-muted-foreground">
                  <MapPin aria-hidden className="size-3.5" />
                  {group.seller.city}
                </span>
              </header>
              <ul className="flex flex-col divide-y divide-border">
                {group.lines.map((line) => (
                  <li key={line.productId} className="flex flex-wrap gap-3 py-3 first:pt-0 last:pb-0">
                    {line.coverPhoto ? (
                      <Image
                        src={photoUrl(line.coverPhoto.cardKey)}
                        alt={line.name}
                        width={cardSize(line.coverPhoto).width}
                        height={cardSize(line.coverPhoto).height}
                        unoptimized
                        className="size-16 shrink-0 rounded-lg object-cover"
                      />
                    ) : (
                      <span aria-hidden className="size-16 shrink-0 rounded-lg bg-muted" />
                    )}
                    <div className="flex min-w-0 grow flex-col gap-1">
                      <Link className="font-medium break-words hover:underline" href={`/products/${line.slug}`}>
                        {line.name}
                      </Link>
                      <span className="text-sm text-muted-foreground tabular-nums">
                        {formatMoney(line.unitPrice, locale)} {t("perUnit", { unit: line.unit })}
                        {!editable && <> × {line.quantity}</>}
                      </span>
                      {!line.available && (
                        <Badge variant="destructive" className="self-start">
                          {t("line.unavailable")}
                        </Badge>
                      )}
                    </div>
                    <span className="shrink-0 font-medium tabular-nums">{formatMoney(line.lineTotal, locale)}</span>
                    {editable && (
                      // Eylemler kendi satırında: dar ekranda adet seçici ve fiyat yan yana sığmaz.
                      <div className="w-full pl-19">
                        <CartLineControls
                          productId={line.productId}
                          productName={line.name}
                          quantity={line.quantity}
                          stock={line.stockQuantity}
                          available={line.available}
                        />
                      </div>
                    )}
                  </li>
                ))}
              </ul>
              <dl className="flex flex-col gap-1 border-t border-border pt-3 text-sm">
                <div className="flex justify-between gap-3">
                  <dt className="text-muted-foreground">{t("group.items")}</dt>
                  <dd className="tabular-nums">{formatMoney(group.itemsTotal, locale)}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-muted-foreground">{t("group.shipping")}</dt>
                  <dd className="tabular-nums">
                    {group.shippingFee.amount === 0 && group.itemsTotal.amount > 0
                      ? t("group.freeShipping")
                      : formatMoney(group.shippingFee, locale)}
                  </dd>
                </div>
                {group.shippingFee.amount > 0 && (
                  <p className="text-muted-foreground">
                    {t("group.freeShippingRemaining", { amount: formatMoney(group.freeShippingRemaining, locale) })}
                  </p>
                )}
                {group.dispatchDays > 0 && (
                  <p className="inline-flex items-center gap-1.5 text-muted-foreground">
                    <Truck aria-hidden className="size-3.5" />
                    {t("group.dispatch", { days: group.dispatchDays })}
                  </p>
                )}
              </dl>
            </CardContent>
          </Card>
        </li>
      ))}
    </ul>
  );
}

import { Printer, Truck } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import type { Shipment, ShipmentStatus } from "@/features/orders/api";
import { formatDateTime } from "@/lib/datetime";
import { CopyCode } from "./CopyCode";

/**
 * Satıcı siparişinin gönderisi (shipments ve orders spec'leri, add-shipments
 * D4). Alıcı ve satıcı aynı kartı görür: firma, takip numarası, durum ve
 * zamanlar. Satıcıya ek olarak etiket bağlantısı (yeni sekmede açılır ve
 * oradan yazdırılır) ve şubede söylenecek büyük puntolu gönderi kodu verilir.
 * Renk tek başına anlam taşımaz; durum metinle söylenir (§12.4).
 */
const statusVariants = {
  CREATED: "secondary",
  HANDED_OVER: "info",
  IN_TRANSIT: "info",
  DELIVERED: "success",
  DELIVERY_FAILED: "destructive",
  CANCELLED: "secondary",
} as const satisfies Record<ShipmentStatus, "secondary" | "info" | "success" | "destructive">;

type ShipmentCardProps = {
  readonly shipment: Shipment;
  /** Yalnızca satıcı ekranında: etiketin BFF adresi. */
  readonly labelHref?: string;
};

export function ShipmentCard({ shipment, labelHref }: ShipmentCardProps) {
  const t = useTranslations("shipments");
  const locale = useLocale();
  const forSeller = labelHref !== undefined;
  return (
    <section className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 text-sm" aria-label={t("title")}>
      <header className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="inline-flex items-center gap-1.5 font-semibold tracking-tight">
          <Truck aria-hidden className="size-4" />
          {shipment.carrier}
        </h2>
        <Badge variant={statusVariants[shipment.status]}>{t("status", { status: shipment.status })}</Badge>
      </header>
      <div className="flex flex-col gap-1">
        <span className="text-muted-foreground">{forSeller ? t("codeLabel") : t("trackingLabel")}</span>
        <span className={forSeller ? "text-2xl font-semibold tracking-wider tabular-nums select-all" : "font-medium tabular-nums select-all"}>
          {shipment.trackingNumber}
        </span>
      </div>
      {forSeller && shipment.status === "CREATED" && (
        <>
          <p className="text-pretty text-muted-foreground">{t("handoverHint")}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <a
              className={buttonVariants({ variant: "default", size: "lg" })}
              href={labelHref}
              target="_blank"
              rel="noopener"
            >
              <Printer aria-hidden />
              {t("openLabel")}
            </a>
            <CopyCode code={shipment.trackingNumber} />
          </div>
        </>
      )}
      <dl className="flex flex-col gap-1 text-muted-foreground">
        {shipment.handedOverAt && (
          <div className="flex justify-between gap-3">
            <dt>{t("handedOverAt")}</dt>
            <dd className="tabular-nums">{formatDateTime(shipment.handedOverAt, locale)}</dd>
          </div>
        )}
        {shipment.deliveredAt && (
          <div className="flex justify-between gap-3">
            <dt>{t("deliveredAt")}</dt>
            <dd className="tabular-nums">{formatDateTime(shipment.deliveredAt, locale)}</dd>
          </div>
        )}
      </dl>
    </section>
  );
}

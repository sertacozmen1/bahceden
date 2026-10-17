import Image from "next/image";
import { MessageSquareWarning } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import type { DefectClaimStatus, DefectClaimView } from "@/features/claims/api";
import { claimBffPaths } from "@/features/claims/bff";
import { formatDateTime } from "@/lib/datetime";
import { formatMoney } from "@/lib/money";
import { cardSize } from "@/lib/productPhotos";

/**
 * Ayıplı ürün talebinin kartı (ADR-086): durum, satırlar ve bildirilen,
 * önerilen ve iade edilen adetler, açıklama, fotoğraflar, satıcının gerekçesi,
 * tarafların süresi ve iade tutarı. Alıcı ve satıcı aynı kartı görür; süre
 * cümlesi tarafa göre değişir, eylemler çağıran sayfadan `actions` ile gelir.
 * Fotoğraflar korumalı medya yolundan gelir; küçük boyut gösterilir, büyük boyut
 * yeni sekmede açılır.
 * Renk tek başına anlam taşımaz; durum metinle yazılır (AGENTS.md §12.4).
 */
const statusVariants = {
  OPEN: "warning",
  OFFERED: "info",
  REJECTED: "secondary",
  ESCALATED: "warning",
  REFUNDED: "success",
  CLOSED: "secondary",
} as const satisfies Record<DefectClaimStatus, "warning" | "info" | "secondary" | "success">;

type ClaimCardProps = {
  readonly claim: DefectClaimView;
  /** Süre cümleleri tarafa göre; admin tarafsız cümleleri görür. */
  readonly viewer: Viewer;
  readonly actions?: React.ReactNode;
};

type Viewer = "buyer" | "seller" | "admin";

/** Süre cümleleri: her taraf kendi cümlesini, admin tarafsız olanı görür. */
const deadlineKeys = {
  buyer: { seller: "sellerDeadlineForBuyer", buyer: "buyerDeadlineForBuyer" },
  seller: { seller: "sellerDeadlineForSeller", buyer: "buyerDeadlineForSeller" },
  admin: { seller: "sellerDeadlineForAdmin", buyer: "buyerDeadlineForAdmin" },
} as const satisfies Record<Viewer, { seller: string; buyer: string }>;

export function ClaimCard({ claim, viewer, actions }: ClaimCardProps) {
  const t = useTranslations("claims");
  const tOrders = useTranslations("orders");
  const locale = useLocale();
  return (
    <section className="flex flex-col gap-3 rounded-lg border border-border p-3 text-sm" aria-labelledby={`claim-${claim.id}`}>
      <header className="flex flex-wrap items-center justify-between gap-2">
        <h3 id={`claim-${claim.id}`} className="inline-flex items-center gap-1.5 font-semibold tracking-tight">
          <MessageSquareWarning aria-hidden className="size-4" />
          {t("cardTitle")}
        </h3>
        <Badge variant={statusVariants[claim.status]}>{t("status", { status: claim.status })}</Badge>
      </header>
      <ul className="flex flex-col gap-1">
        {claim.lines.map((line) => (
          <li key={line.orderItemId} className="flex justify-between gap-3">
            <span className="min-w-0 break-words">{line.productName}</span>
            <span className="flex shrink-0 flex-col items-end text-muted-foreground tabular-nums">
              <span>
                {t("claimedQuantity", { quantity: line.claimedQuantity })} {tOrders("perUnit", { unit: line.unit })}
              </span>
              {line.offeredQuantity != null && line.refundedQuantity == null && (
                <span>{t("offeredQuantity", { quantity: line.offeredQuantity })}</span>
              )}
              {line.refundedQuantity != null && (
                <span className="text-foreground">{t("refundedQuantity", { quantity: line.refundedQuantity })}</span>
              )}
            </span>
          </li>
        ))}
      </ul>
      <p className="break-words whitespace-pre-line text-muted-foreground">{claim.description}</p>
      <ul className="flex flex-wrap gap-2" aria-label={t("photosLabel")}>
        {claim.photos.map((photo) => {
          const size = cardSize(photo);
          return (
            <li key={photo.position}>
              <a
                href={claimBffPaths.photo(claim.id, photo.position, "LARGE")}
                target="_blank"
                rel="noopener"
                className="block overflow-hidden rounded-md border border-border"
              >
                <Image
                  src={claimBffPaths.photo(claim.id, photo.position, "CARD")}
                  alt={t("photoAlt", { position: photo.position + 1 })}
                  width={size.width}
                  height={size.height}
                  unoptimized
                  className="size-20 object-cover"
                />
              </a>
            </li>
          );
        })}
      </ul>
      {claim.sellerReason && <p className="break-words">{t("sellerReason", { reason: claim.sellerReason })}</p>}
      {(claim.status === "OFFERED" || claim.status === "REJECTED") && claim.buyerDeadlineAt && (
        <p className="text-muted-foreground">
          {t(deadlineKeys[viewer].buyer, {
            status: claim.status,
            date: formatDateTime(claim.buyerDeadlineAt, locale),
          })}
        </p>
      )}
      {claim.refundAmount && (
        <p className="rounded-lg bg-success/10 px-3 py-2 text-success">
          {t("refunded", { amount: formatMoney(claim.refundAmount, locale) })}
          {claim.shippingRefunded && <span className="block">{t("shippingRefunded")}</span>}
        </p>
      )}
      {claim.status === "OPEN" && (
        <p className="text-muted-foreground">
          {t(deadlineKeys[viewer].seller, {
            date: formatDateTime(claim.sellerDeadlineAt, locale),
          })}
        </p>
      )}
      {actions}
    </section>
  );
}

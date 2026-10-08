import { FileText } from "lucide-react";
import { useTranslations } from "next-intl";
import { buttonVariants } from "@/components/ui/button";
import type { SellerSelfView } from "@/features/seller/api";
import { sellerBffPaths } from "@/features/seller/bff";
import { SellerStatusBadge } from "./SellerStatusBadge";

/**
 * Kaydın sahibine kendi beyanı, durumu ve varsa gerekçesi. Belge yeni sekmede
 * açılır; adres BFF'tir, belgenin herkese açık bir adresi yoktur (D6).
 */
export function SellerRecordSummary({ seller }: { readonly seller: SellerSelfView }) {
  const t = useTranslations("seller.application");
  return (
    <div className="flex flex-col gap-5">
      <dl className="grid gap-4">
        <div className="grid gap-1.5">
          <dt className="text-sm text-muted-foreground">{t("status.statusLabel")}</dt>
          <dd>
            <SellerStatusBadge status={seller.status} />
          </dd>
        </div>
        {seller.statusReason !== undefined && seller.statusReason !== null && (
          <div className="grid gap-1">
            <dt className="text-sm text-muted-foreground">{t("status.reason")}</dt>
            <dd className="text-pretty">{seller.statusReason}</dd>
          </div>
        )}
        <div className="grid gap-1">
          <dt className="text-sm text-muted-foreground">{t("fields.displayName")}</dt>
          <dd className="font-medium break-words">{seller.displayName}</dd>
        </div>
        <div className="grid gap-1">
          <dt className="text-sm text-muted-foreground">{t("fields.city")}</dt>
          <dd className="font-medium">{seller.city}</dd>
        </div>
        <div className="grid gap-1">
          <dt className="text-sm text-muted-foreground">{t("fields.phone")}</dt>
          <dd className="font-medium">{seller.phone}</dd>
        </div>
        <div className="grid gap-1">
          <dt className="text-sm text-muted-foreground">{t("fields.documentType")}</dt>
          <dd className="font-medium">{t("documentType", { type: seller.documentType })}</dd>
        </div>
      </dl>
      <a
        className={buttonVariants({ variant: "outline", className: "self-start" })}
        href={sellerBffPaths.document}
        target="_blank"
        rel="noopener"
      >
        <FileText aria-hidden />
        {t("status.viewDocument")}
      </a>
    </div>
  );
}

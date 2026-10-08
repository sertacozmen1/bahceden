import Link from "next/link";
import { ShieldAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { buttonVariants } from "@/components/ui/button";
import { StatusPanel } from "@/components/StatusPanel";
import { ApiRequestError } from "@/lib/apiError";

/**
 * Rolü token'da duran ama kaydı onaylı olmayan satıcı (ADR-044): `api` 403
 * `SELLER_NOT_APPROVED` döndürdüğünde gösterilir; başvuru sayfasına yönlendirir.
 */
export function SellerNotApproved() {
  const t = useTranslations("listings.list");
  return (
    <StatusPanel icon={ShieldAlert} title={t("notApprovedTitle")} body={t("notApprovedBody")}>
      <Link className={buttonVariants()} href="/seller/application">
        {t("notApprovedAction")}
      </Link>
    </StatusPanel>
  );
}

/** `api`'nin onaylı olmayan satıcı yanıtı mı? Sayfalar bu durumu hata yerine yukarıdaki panelle gösterir. */
export function isSellerNotApproved(error: unknown): boolean {
  return error instanceof ApiRequestError && error.code === "SELLER_NOT_APPROVED";
}

import Link from "next/link";
import { Store } from "lucide-react";
import { useTranslations } from "next-intl";
import { buttonVariants } from "@/components/ui/button";
import type { SellerSelfView } from "@/features/seller/api";
import { SellerStatusBadge } from "./SellerStatusBadge";

/**
 * "Hesabım" ekranındaki satıcılık bölümü: kaydı olmayan hesabı başvuruya davet
 * eder, kaydı olanın durumunu gösterir. Her iki durumda tek eylem vardır ve
 * başvuru sayfasına gider.
 */
export function SellerCard({ seller }: { readonly seller: SellerSelfView | null }) {
  const t = useTranslations("seller.card");
  return (
    <section aria-labelledby="seller-card-title" className="flex flex-col gap-3">
      <h2 id="seller-card-title" className="text-base font-semibold tracking-tight">
        {t("title")}
      </h2>
      {seller === null ? (
        <p className="text-sm text-pretty text-muted-foreground">{t("inviteBody")}</p>
      ) : (
        <div>
          <SellerStatusBadge status={seller.status} />
        </div>
      )}
      <Link className={buttonVariants({ variant: "secondary", className: "self-start" })} href="/seller/application">
        <Store aria-hidden />
        {seller === null ? t("inviteAction") : t("statusAction")}
      </Link>
    </section>
  );
}

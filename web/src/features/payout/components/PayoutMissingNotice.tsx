import Link from "next/link";
import { useTranslations } from "next-intl";
import { FormMessage } from "@/features/account/components/FormMessage";

/**
 * Ödeme hesabı etkin olmayan satıcıya ilan ekranlarında gösterilen uyarı
 * (add-cart-and-checkout D1, D10): ilanlar yayında olsa bile alıcı satın
 * alamaz. Ödeme bilgileri sayfasına götürür.
 */
export function PayoutMissingNotice() {
  const t = useTranslations("payout.notice");
  return (
    <div className="mb-6">
      <FormMessage tone="info">
        <strong className="block">{t("title")}</strong>
        {t("body")}{" "}
        <Link className="font-medium underline underline-offset-4" href="/seller/payout">
          {t("action")}
        </Link>
      </FormMessage>
    </div>
  );
}

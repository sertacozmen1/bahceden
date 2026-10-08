import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Card, CardContent } from "@/components/ui/card";
import { FormMessage } from "@/features/account/components/FormMessage";
import { requireRole } from "@/features/account/requireSession";
import { isSellerNotApproved, SellerNotApproved } from "@/features/listings/components/SellerNotApproved";
import { getOwnPayoutAccount, type PayoutAccountView } from "@/features/payout/api";
import { PayoutForm } from "@/features/payout/components/PayoutForm";
import { SellerNav } from "@/features/seller/components/SellerNav";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("payout.title") };
}

/**
 * Satıcının ödeme bilgileri (seller-payout-details spec'i, add-cart-and-checkout
 * D2). Kayıtlı hesap maskeli bir özetle gösterilir; durum etkin değilse
 * ürünlerin satın alınamayacağı açıkça söylenir. Onaylı olmayan satıcı hata
 * yerine başvuru yönlendirmesi görür (ADR-044).
 */
export default async function SellerPayoutPage() {
  const t = await getTranslations();
  const { accessToken } = await requireRole("/seller/payout", "ROLE_SELLER");

  let account: PayoutAccountView | null;
  try {
    account = await getOwnPayoutAccount(accessToken);
  } catch (error) {
    if (isSellerNotApproved(error)) {
      return <SellerNotApproved />;
    }
    throw error;
  }

  return (
    <main className="mx-auto w-full max-w-xl px-4 pt-8 pb-12 lg:pt-12">
      <header className="mb-6 flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t("payout.title")}</h1>
        <p className="max-w-prose text-pretty text-muted-foreground">{t("payout.intro")}</p>
      </header>
      <SellerNav current="payout" />
      <div className="flex flex-col gap-6">
        {account === null ? (
          <FormMessage tone="info">{t("payout.missing")}</FormMessage>
        ) : (
          <Card>
            <CardContent className="flex flex-col gap-4">
              {account.status === "ACTIVE" ? (
                <FormMessage tone="success">{t("payout.active")}</FormMessage>
              ) : (
                <FormMessage tone="error">{t("payout.rejected")}</FormMessage>
              )}
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <div className="flex flex-col gap-0.5">
                  <dt className="text-muted-foreground">{t("payout.form.legalType")}</dt>
                  <dd className="font-medium">{t("payout.form.legalTypeOption", { type: account.legalType })}</dd>
                </div>
                <div className="flex flex-col gap-0.5">
                  <dt className="text-muted-foreground">{t("payout.summary.legalName")}</dt>
                  <dd className="font-medium break-words">{account.legalName}</dd>
                </div>
                <div className="flex flex-col gap-0.5">
                  <dt className="text-muted-foreground">{t("payout.summary.taxId")}</dt>
                  <dd className="font-medium tabular-nums">{account.taxIdMasked}</dd>
                </div>
                {account.taxOffice !== undefined && account.taxOffice !== null && (
                  <div className="flex flex-col gap-0.5">
                    <dt className="text-muted-foreground">{t("payout.form.taxOffice")}</dt>
                    <dd className="font-medium break-words">{account.taxOffice}</dd>
                  </div>
                )}
                <div className="flex flex-col gap-0.5 sm:col-span-2">
                  <dt className="text-muted-foreground">{t("payout.form.iban")}</dt>
                  <dd className="font-medium tabular-nums break-all">{account.ibanMasked}</dd>
                </div>
              </dl>
            </CardContent>
          </Card>
        )}
        <Card>
          <CardContent className="flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <h2 className="text-lg font-semibold tracking-tight">
                {account === null ? t("payout.form.createTitle") : t("payout.form.updateTitle")}
              </h2>
              <p className="text-sm text-pretty text-muted-foreground">{t("payout.form.privacy")}</p>
            </div>
            <PayoutForm account={account} />
          </CardContent>
        </Card>
      </div>
    </main>
  );
}

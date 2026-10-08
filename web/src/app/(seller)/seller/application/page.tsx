import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { ResendVerificationButton } from "@/features/account/components/ResendVerificationButton";
import { requireSignedIn } from "@/features/account/requireSession";
import { getOwnSeller } from "@/features/seller/api";
import { SellerApplicationForm } from "@/features/seller/components/SellerApplicationForm";
import { SellerRecordSummary } from "@/features/seller/components/SellerRecordSummary";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("seller.application.title") };
}

/**
 * Satıcı başvurusu (seller-onboarding spec'i). İçerik kaydın durumuna göre
 * değişir; kayıt her istekte `api`'den taze okunur:
 *
 * - kayıt yok, e-posta doğrulanmamış: önce doğrulama (D4);
 * - kayıt yok: başvuru formu;
 * - `REJECTED`: gerekçe ve önceki bilgilerle dolu form (yeniden gönderim);
 * - `PENDING`, `APPROVED`, `SUSPENDED`: durum, beyan ve belge.
 *
 * Yetkisiz durum `layout.tsx` kapısında ele alınır.
 */
export default async function SellerApplicationPage() {
  const t = await getTranslations();
  const { account, accessToken } = await requireSignedIn("/seller/application");
  const seller = await getOwnSeller(accessToken);

  if (seller === null) {
    if (!account.emailVerified) {
      return (
        <AccountScreen title={t("seller.application.verifyFirst.title")} intro={t("seller.application.verifyFirst.body")}>
          <ResendVerificationButton />
        </AccountScreen>
      );
    }
    return (
      <AccountScreen title={t("seller.application.title")} intro={t("seller.application.intro")}>
        <SellerApplicationForm mode="apply" />
      </AccountScreen>
    );
  }

  if (seller.status === "REJECTED") {
    return (
      <AccountScreen title={t("seller.application.status.rejectedTitle")} intro={t("seller.application.status.rejectedBody")}>
        <dl className="grid gap-1">
          <dt className="text-sm text-muted-foreground">{t("seller.application.status.reason")}</dt>
          <dd className="text-pretty">{seller.statusReason}</dd>
        </dl>
        <SellerApplicationForm mode="resubmit" previous={seller} />
      </AccountScreen>
    );
  }

  const heading = {
    PENDING: { title: t("seller.application.status.pendingTitle"), body: t("seller.application.status.pendingBody") },
    APPROVED: { title: t("seller.application.status.approvedTitle"), body: t("seller.application.status.approvedBody") },
    SUSPENDED: { title: t("seller.application.status.suspendedTitle"), body: t("seller.application.status.suspendedBody") },
  }[seller.status];

  return (
    <AccountScreen title={heading.title} intro={heading.body}>
      <SellerRecordSummary seller={seller} />
    </AccountScreen>
  );
}

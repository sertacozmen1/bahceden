import type { Metadata } from "next";
import Link from "next/link";
import { CircleX } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { ResetPasswordForm } from "@/features/account/components/ResetPasswordForm";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("account.resetPassword.title") };
}

/**
 * E-postadaki sıfırlama bağlantısının indiği sayfa (ADR-033, ADR-035).
 *
 * Doğrulama sayfasından farklı olarak token sayfa açılırken tüketilmez:
 * kullanıcı önce yeni parolasını yazar. Bu yüzden e-posta istemcilerinin
 * bağlantıyı önceden açması token'ı harcamaz. Token yoksa form gösterilmez,
 * yeni bağlantı isteme yolu sunulur.
 */
export default async function ResetPasswordPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly token?: string }>;
}) {
  const { token } = await searchParams;
  const t = await getTranslations();
  const requestNewLink = (
    <Link className="font-medium text-primary underline-offset-4 hover:underline" href="/forgot-password">
      {t("account.resetPassword.requestNewLink")}
    </Link>
  );

  if (token === undefined || token.trim() === "") {
    return (
      <AccountScreen panelRole="alert">
        <span className="grid size-11 place-items-center rounded-full bg-destructive/10 text-destructive">
          <CircleX aria-hidden className="size-5.5" />
        </span>
        <div className="flex flex-col gap-2">
          <h1 className="text-xl font-semibold tracking-tight">{t("account.resetPassword.missingTitle")}</h1>
          <p className="text-pretty text-muted-foreground">{t("account.resetPassword.missingToken")}</p>
        </div>
        <Link className={buttonVariants()} href="/forgot-password">
          {t("account.resetPassword.requestNewLink")}
        </Link>
      </AccountScreen>
    );
  }

  return (
    <AccountScreen
      title={t("account.resetPassword.title")}
      intro={t("account.resetPassword.intro")}
      aside={requestNewLink}
    >
      <ResetPasswordForm token={token} />
    </AccountScreen>
  );
}

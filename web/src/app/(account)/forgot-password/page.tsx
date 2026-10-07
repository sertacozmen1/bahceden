import type { Metadata } from "next";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { ForgotPasswordForm } from "@/features/account/components/ForgotPasswordForm";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("account.forgotPassword.title") };
}

/** Parola sıfırlama talebi (account-identity spec'i "Parola sıfırlama", ADR-034). */
export default function ForgotPasswordPage() {
  const t = useTranslations();
  return (
    <AccountScreen
      title={t("account.forgotPassword.title")}
      intro={t("account.forgotPassword.intro")}
      aside={
        <>
          {t("account.forgotPassword.remembered")}{" "}
          <Link className="font-medium text-primary underline-offset-4 hover:underline" href="/login">
            {t("account.nav.signIn")}
          </Link>
        </>
      }
    >
      <ForgotPasswordForm />
    </AccountScreen>
  );
}

import type { Metadata } from "next";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { RegisterForm } from "@/features/account/components/RegisterForm";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("account.register.title") };
}

/** Kayıt sayfası (account-identity spec'i "E-posta ve parola ile hesap açma"). */
export default function RegisterPage() {
  const t = useTranslations();
  return (
    <AccountScreen
      title={t("account.register.title")}
      intro={t("account.register.intro")}
      aside={
        <>
          {t("account.register.haveAccount")}{" "}
          <Link className="font-medium text-primary underline-offset-4 hover:underline" href="/login">
            {t("account.nav.signIn")}
          </Link>
        </>
      }
    >
      <RegisterForm />
    </AccountScreen>
  );
}

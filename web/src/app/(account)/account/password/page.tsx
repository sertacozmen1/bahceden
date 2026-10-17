import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { requireSignedIn } from "@/features/account/requireSession";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { ChangePasswordForm } from "@/features/account/components/ChangePasswordForm";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("account.changePassword.title") };
}

/**
 * Oturum açıkken parola değiştirme (add-password-change D4).
 *
 * Yetkisiz durum Hesabım kapısında ele alınır; sayfa kendi yolunu dönüş adresi
 * olarak verir. Giriş cümlesi sonucu önceden söyler: bu cihaz dahil bütün
 * oturumlar kapanır (§12.5). Yükleniyor durumu Hesabım'ın iskeletini kullanır.
 */
export default async function ChangePasswordPage() {
  const t = await getTranslations();
  await requireSignedIn("/account/password");
  return (
    <AccountScreen
      title={t("account.changePassword.title")}
      intro={t("account.changePassword.intro")}
      aside={
        <Link className="font-medium text-primary underline-offset-4 hover:underline" href="/account">
          {t("account.changePassword.back")}
        </Link>
      }
    >
      <ChangePasswordForm />
    </AccountScreen>
  );
}

import type { Metadata } from "next";
import Link from "next/link";
import { ShieldCheck, ShieldOff } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { Badge } from "@/components/ui/badge";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { ResendVerificationButton } from "@/features/account/components/ResendVerificationButton";
import { TwoFactorForm } from "@/features/account/components/TwoFactorForm";
import { requireSignedIn } from "@/features/account/requireSession";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("account.twoFactor.title") };
}

/**
 * İki adımlı girişi açma ve kapatma (add-two-factor-toggle D2, ADR-104).
 *
 * Durum güncel hesaptan okunur. Açmak doğrulanmış e-posta ister (ADR-103):
 * e-postası doğrulanmamış ve ikinci adımı kapalı kullanıcıya form yerine
 * doğrulama yönlendirmesi gösterilir (ödeme sayfasındaki desen). Kapatmak
 * doğrulama istemez. Yetkisiz durum Hesabım kapısında; yükleniyor durumu
 * Hesabım'ın iskeletini kullanır.
 */
export default async function TwoFactorPage() {
  const t = await getTranslations();
  const { account } = await requireSignedIn("/account/two-factor");
  const enabled = account.twoFactorEnabled;
  return (
    <AccountScreen
      title={t("account.twoFactor.title")}
      intro={t("account.twoFactor.intro")}
      aside={
        <Link className="font-medium text-primary underline-offset-4 hover:underline" href="/account">
          {t("account.twoFactor.back")}
        </Link>
      }
    >
      <dl className="grid gap-1.5">
        <dt className="text-sm text-muted-foreground">{t("account.twoFactor.status")}</dt>
        <dd>
          {enabled ? (
            <Badge variant="success">
              <ShieldCheck aria-hidden />
              {t("account.twoFactor.on")}
            </Badge>
          ) : (
            <Badge variant="secondary">
              <ShieldOff aria-hidden />
              {t("account.twoFactor.off")}
            </Badge>
          )}
        </dd>
      </dl>
      {!enabled && !account.emailVerified ? (
        <section className="flex flex-col gap-3">
          <h2 className="text-base font-semibold tracking-tight">{t("account.twoFactor.verifyFirstTitle")}</h2>
          <p className="text-sm text-pretty text-muted-foreground">{t("account.twoFactor.verifyFirstBody")}</p>
          <ResendVerificationButton />
        </section>
      ) : (
        <TwoFactorForm enabled={enabled} />
      )}
    </AccountScreen>
  );
}

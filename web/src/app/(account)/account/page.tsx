import type { Metadata } from "next";
import Link from "next/link";
import { BadgeCheck, CircleAlert, KeyRound, MapPinHouse, Receipt, ShieldCheck } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { requireSignedIn } from "@/features/account/requireSession";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { ResendVerificationButton } from "@/features/account/components/ResendVerificationButton";
import { SignOutButton } from "@/features/account/components/SignOutButton";
import { getOwnSeller } from "@/features/seller/api";
import { SellerCard } from "@/features/seller/components/SellerCard";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("account.myAccount.title") };
}

/**
 * Hesabım: korunan ilk sayfa (account-identity spec'i "Oturum sahibinin kimliği").
 *
 * Yetkisiz durum (AGENTS.md §12.5) `layout.tsx` kapısında ele alınır; sayfa
 * aynı kapıdan oturumu alır. Veri her istekte `api`'den taze okunur (D7).
 *
 * Doğrulama durumu bir etikettir ve metniyle okunur; doğrulanmamışsa yeni
 * bağlantı eylemi hemen altında durur (tek odak). Çıkış en altta, ayraçla
 * ayrılmış: sık kullanılmaz ve birincil eylemle karışmamalı (Fitts).
 *
 * Satıcılık bölümü kaydı olmayanı başvuruya davet eder, olanın durumunu
 * gösterir (add-seller-onboarding); kayıt her istekte taze okunur.
 *
 * Alışveriş bağlantıları (siparişler, adresler) satıcılıktan önce durur: hesabı olan
 * herkes alıcıdır (add-cart-and-checkout D10).
 *
 * Güvenlik bölümü yalnızca bağlantı verir; parola formu (add-password-change
 * D4) ve iki adımlı giriş (add-two-factor-toggle D2) kendi sayfalarındadır,
 * Hesabım tek odakta kalır. İki adımlı girişin durumu bağlantıda görünür.
 */
export default async function MyAccountPage() {
  const t = await getTranslations();
  const { account, accessToken } = await requireSignedIn("/account");
  const seller = await getOwnSeller(accessToken);
  return (
    <AccountScreen title={t("account.myAccount.title")}>
      <dl className="grid gap-4">
        <div className="grid gap-1">
          <dt className="text-sm text-muted-foreground">{t("account.myAccount.email")}</dt>
          <dd className="font-medium break-all">{account.email}</dd>
        </div>
        <div className="grid gap-1">
          <dt className="text-sm text-muted-foreground">{t("account.myAccount.roles")}</dt>
          <dd className="font-medium">{account.roles.map((role) => t("account.myAccount.role", { role: role })).join(", ")}</dd>
        </div>
        <div className="grid gap-1.5">
          <dt className="text-sm text-muted-foreground">{t("account.myAccount.verificationStatus")}</dt>
          <dd>
            {account.emailVerified ? (
              <Badge variant="success">
                <BadgeCheck aria-hidden />
                {t("account.myAccount.verified")}
              </Badge>
            ) : (
              <Badge variant="warning">
                <CircleAlert aria-hidden />
                {t("account.myAccount.notVerified")}
              </Badge>
            )}
          </dd>
        </div>
      </dl>
      {!account.emailVerified && (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-pretty text-muted-foreground">{t("account.myAccount.notVerifiedBody")}</p>
          <ResendVerificationButton />
        </div>
      )}
      <Separator />
      <nav aria-label={t("account.myAccount.shopping")} className="flex flex-col gap-3">
        <h2 className="text-base font-semibold tracking-tight">{t("account.myAccount.shopping")}</h2>
        <div className="flex flex-wrap gap-2">
          <Link className={buttonVariants({ variant: "outline" })} href="/account/orders">
            <Receipt aria-hidden />
            {t("orders.title")}
          </Link>
          <Link className={buttonVariants({ variant: "outline" })} href="/account/addresses">
            <MapPinHouse aria-hidden />
            {t("addresses.title")}
          </Link>
        </div>
      </nav>
      <Separator />
      <nav aria-label={t("account.myAccount.security")} className="flex flex-col gap-3">
        <h2 className="text-base font-semibold tracking-tight">{t("account.myAccount.security")}</h2>
        <div className="flex flex-wrap gap-2">
          <Link className={buttonVariants({ variant: "outline" })} href="/account/password">
            <KeyRound aria-hidden />
            {t("account.myAccount.changePassword")}
          </Link>
          <Link className={buttonVariants({ variant: "outline" })} href="/account/two-factor">
            <ShieldCheck aria-hidden />
            {t("account.myAccount.twoFactor")}
            <Badge variant={account.twoFactorEnabled ? "success" : "secondary"}>
              {t(account.twoFactorEnabled ? "account.twoFactor.on" : "account.twoFactor.off")}
            </Badge>
          </Link>
        </div>
      </nav>
      <Separator />
      <SellerCard seller={seller} />
      <Separator />
      <SignOutButton variant="ghost" className="self-start" />
    </AccountScreen>
  );
}

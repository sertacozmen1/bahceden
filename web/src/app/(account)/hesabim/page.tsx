import type { Metadata } from "next";
import { BadgeCheck, CircleAlert } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { requireSignedIn } from "@/features/account/requireSession";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { ResendVerificationButton } from "@/features/account/components/ResendVerificationButton";
import { SignOutButton } from "@/features/account/components/SignOutButton";
import { roleLabels, texts } from "@/lib/texts";

export const metadata: Metadata = { title: texts.account.myAccount.title };

/**
 * Hesabım: korunan ilk sayfa (account-identity spec'i "Oturum sahibinin kimliği").
 *
 * Yetkisiz durum (AGENTS.md §12.5) `layout.tsx` kapısında ele alınır; sayfa
 * aynı kapıdan oturumu alır. Veri her istekte `api`'den taze okunur (D7).
 *
 * Doğrulama durumu bir etikettir ve metniyle okunur; doğrulanmamışsa yeni
 * bağlantı eylemi hemen altında durur (tek odak). Çıkış en altta, ayraçla
 * ayrılmış: sık kullanılmaz ve birincil eylemle karışmamalı (Fitts).
 */
export default async function MyAccountPage() {
  const { account } = await requireSignedIn("/hesabim");
  return (
    <AccountScreen title={texts.account.myAccount.title}>
      <dl className="grid gap-4">
        <div className="grid gap-1">
          <dt className="text-sm text-muted-foreground">{texts.account.myAccount.email}</dt>
          <dd className="font-medium break-all">{account.email}</dd>
        </div>
        <div className="grid gap-1">
          <dt className="text-sm text-muted-foreground">{texts.account.myAccount.roles}</dt>
          <dd className="font-medium">{account.roles.map((role) => roleLabels[role]).join(", ")}</dd>
        </div>
        <div className="grid gap-1.5">
          <dt className="text-sm text-muted-foreground">{texts.account.myAccount.verificationStatus}</dt>
          <dd>
            {account.emailVerified ? (
              <Badge variant="secondary">
                <BadgeCheck aria-hidden />
                {texts.account.myAccount.verified}
              </Badge>
            ) : (
              <Badge variant="warning">
                <CircleAlert aria-hidden />
                {texts.account.myAccount.notVerified}
              </Badge>
            )}
          </dd>
        </div>
      </dl>
      {!account.emailVerified && (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-pretty text-muted-foreground">{texts.account.myAccount.notVerifiedBody}</p>
          <ResendVerificationButton />
        </div>
      )}
      <Separator />
      <SignOutButton variant="ghost" className="self-start" />
    </AccountScreen>
  );
}

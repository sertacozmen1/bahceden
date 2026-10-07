import type { Metadata } from "next";
import Link from "next/link";
import { headers } from "next/headers";
import { CircleCheck, CircleX } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { verifyEmail } from "@/features/account/api";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { ApiRequestError } from "@/lib/apiError";
import { cn } from "@/lib/utils";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("account.verification.title") };
}

/**
 * E-postadaki doğrulama bağlantısının indiği sayfa (design.md D3, D9).
 *
 * Token sunucu tarafında tüketilir. Sonuç girişi etkilemez (ADR-017):
 * başarıda da başarısızlıkta da kullanıcı yoluna devam edebilir. `api`
 * reddederse sunucunun mesajı olduğu gibi gösterilir; o mesaj yeni bağlantının
 * nasıl isteneceğini zaten söyler, sayfa aynı cümleyi tekrar etmez. `api`'ye
 * ulaşılamazsa istisna yukarı çıkar ve hata durumu (`error.tsx`) yeniden
 * denemeyi sunar.
 */
export default async function VerificationPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly token?: string }>;
}) {
  const { token } = await searchParams;
  const t = await getTranslations();
  const failure = await consume(token);
  const Icon = failure === null ? CircleCheck : CircleX;

  return (
    <AccountScreen panelRole={failure === null ? "status" : "alert"}>
      <span
        className={cn(
          "grid size-11 place-items-center rounded-full",
          failure === null ? "bg-success/10 text-success" : "bg-destructive/10 text-destructive",
        )}
      >
        <Icon aria-hidden className="size-5.5" />
      </span>
      <div className="flex flex-col gap-2">
        <h1 className="text-xl font-semibold tracking-tight">
          {failure === null ? t("account.verification.successTitle") : t("account.verification.failedTitle")}
        </h1>
        <p className="text-pretty text-muted-foreground">{failure ?? t("account.verification.successBody")}</p>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Link className={buttonVariants()} href="/hesabim">
          {t("account.verification.goToAccount")}
        </Link>
        <Link className={buttonVariants({ variant: "ghost" })} href="/">
          {t("account.verification.goToStorefront")}
        </Link>
      </div>
    </AccountScreen>
  );
}

/** Başarıda `null`, reddedilirse kullanıcıya gösterilecek mesaj. */
async function consume(token: string | undefined): Promise<string | null> {
  const t = await getTranslations();
  if (token === undefined || token.trim() === "") {
    return t("account.verification.missingToken");
  }
  try {
    await verifyEmail(token, (await headers()).get("x-forwarded-for"));
    return null;
  } catch (error) {
    if (error instanceof ApiRequestError) {
      return error.message;
    }
    throw error;
  }
}

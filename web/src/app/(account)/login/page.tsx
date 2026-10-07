import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { currentSession } from "@/features/account/api";
import { AccountScreen } from "@/features/account/components/AccountScreen";
import { FormMessage } from "@/features/account/components/FormMessage";
import { LoginForm } from "@/features/account/components/LoginForm";
import { safeReturnPath } from "@/lib/sameOrigin";

/** Sayfa başlığı seçili dilde (ADR-029). */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("account.login.title") };
}

const DEFAULT_RETURN_PATH = "/account";

/**
 * Giriş sayfası. Korunan bir sayfadan yönlendirildiyse (`next`) bunu söyler ve
 * girişten sonra oraya döner. Zaten oturum açıksa doğrudan hedefe gider.
 * Parola sıfırlamadan gelindiyse (`reset`) parolanın değiştiğini söyler:
 * sıfırlama oturum açmaz, kullanıcı burada yeni parolasıyla girer (ADR-035).
 */
export default async function LoginPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly next?: string; readonly reset?: string }>;
}) {
  const { next, reset } = await searchParams;
  const t = await getTranslations();
  const returnPath = safeReturnPath(next, DEFAULT_RETURN_PATH);

  if ((await currentSession()).kind === "signed-in") {
    redirect(returnPath);
  }

  return (
    <AccountScreen
      title={t("account.login.title")}
      intro={next === undefined ? t("account.login.intro") : t("account.login.signInRequired")}
      aside={
        <>
          {t("account.login.noAccount")}{" "}
          <Link className="font-medium text-primary underline-offset-4 hover:underline" href="/register">
            {t("account.nav.register")}
          </Link>
        </>
      }
    >
      {reset === "1" && <FormMessage tone="success">{t("account.login.passwordChanged")}</FormMessage>}
      <LoginForm returnPath={returnPath} />
    </AccountScreen>
  );
}

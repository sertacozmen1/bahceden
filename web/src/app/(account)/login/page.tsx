import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { currentSession } from "@/features/account/api";
import { AccountScreen } from "@/features/account/components/AccountScreen";
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
 */
export default async function LoginPage({
  searchParams,
}: {
  readonly searchParams: Promise<{ readonly next?: string }>;
}) {
  const { next } = await searchParams;
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
      <LoginForm returnPath={returnPath} />
    </AccountScreen>
  );
}

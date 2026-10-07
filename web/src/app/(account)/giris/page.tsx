import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { currentSession } from "@/features/account/api";
import { LoginForm } from "@/features/account/components/LoginForm";
import { safeReturnPath } from "@/lib/sameOrigin";
import { texts } from "@/lib/texts";
import styles from "../account.module.css";

export const metadata: Metadata = { title: texts.account.login.title };

const DEFAULT_RETURN_PATH = "/hesabim";

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
  const returnPath = safeReturnPath(next, DEFAULT_RETURN_PATH);

  if ((await currentSession()).kind === "signed-in") {
    redirect(returnPath);
  }

  return (
    <main className={styles.page}>
      <h1 className={styles.title}>{texts.account.login.title}</h1>
      <p className={styles.intro}>
        {next === undefined ? texts.account.login.intro : texts.account.login.signInRequired}
      </p>
      <div className={styles.panel}>
        <LoginForm returnPath={returnPath} />
      </div>
      <p className={styles.aside}>
        {texts.account.login.noAccount} <Link href="/kayit">{texts.account.nav.register}</Link>
      </p>
    </main>
  );
}

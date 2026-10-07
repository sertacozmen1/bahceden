import type { Metadata } from "next";
import { requireSignedIn } from "@/features/account/requireSession";
import { ResendVerificationButton } from "@/features/account/components/ResendVerificationButton";
import { SignOutButton } from "@/features/account/components/SignOutButton";
import { roleLabels, texts } from "@/lib/texts";
import styles from "../account.module.css";

export const metadata: Metadata = { title: texts.account.myAccount.title };

/**
 * Hesabım: korunan ilk sayfa (account-identity spec'i "Oturum sahibinin kimliği").
 *
 * Yetkisiz durum (AGENTS.md §12.5) `layout.tsx` kapısında ele alınır; sayfa
 * aynı kapıdan oturumu alır. Veri her istekte `api`'den taze okunur (D7).
 */
export default async function MyAccountPage() {
  const { account } = await requireSignedIn("/hesabim");
  return (
    <main className={styles.page}>
      <h1 className={styles.title}>{texts.account.myAccount.title}</h1>
      <section className={styles.panel}>
        <dl className={styles.details}>
          <dt>{texts.account.myAccount.email}</dt>
          <dd>{account.email}</dd>
          <dt>{texts.account.myAccount.roles}</dt>
          <dd>{account.roles.map((role) => roleLabels[role]).join(", ")}</dd>
          <dt>{texts.account.myAccount.verificationStatus}</dt>
          <dd className={account.emailVerified ? undefined : styles.statusWarning}>
            {account.emailVerified ? texts.account.myAccount.verified : texts.account.myAccount.notVerified}
          </dd>
        </dl>
        {!account.emailVerified && (
          <>
            <p>{texts.account.myAccount.notVerifiedBody}</p>
            <ResendVerificationButton />
          </>
        )}
        <SignOutButton className={styles.action} />
      </section>
    </main>
  );
}

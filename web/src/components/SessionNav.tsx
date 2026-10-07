import Link from "next/link";
import { currentSession, type SessionState } from "@/features/account/api";
import { SignOutButton } from "@/features/account/components/SignOutButton";
import { maskEmail } from "@/features/account/maskEmail";
import { texts } from "@/lib/texts";
import styles from "./AppShell.module.css";

/**
 * Üst başlıktaki oturum alanı (tasks 4.6).
 *
 * Oturum durumu istek başına bir kez okunur (`currentSession` önbelleği) ve
 * sayfa aynı sonucu paylaşır. `api`'ye ulaşılamazsa başlık vitrini düşürmez:
 * oturum bilgisi gerektirmeyen nötr bir "Hesabım" bağlantısı gösterilir,
 * asıl hata ilgili sayfanın kendi hata durumunda görünür (AGENTS.md §12.5).
 */
export async function SessionNav() {
  const session = await readSession();

  if (session?.kind === "signed-in") {
    return (
      <nav className={styles.session} aria-label={texts.account.nav.signedInAs(maskEmail(session.account.email))}>
        <Link className={styles.sessionLink} href="/hesabim">
          <span className={styles.sessionEmail}>{maskEmail(session.account.email)}</span>
        </Link>
        <SignOutButton className={styles.sessionButton} />
      </nav>
    );
  }

  if (session === null || session.kind === "expired") {
    // Oturum var ama yenilenmeli ya da durum okunamadı: "Hesabım" yenilemeyi tetikler.
    return (
      <nav className={styles.session} aria-label={texts.account.nav.myAccount}>
        <Link className={styles.sessionLink} href="/hesabim">
          {texts.account.nav.myAccount}
        </Link>
      </nav>
    );
  }

  return (
    <nav className={styles.session} aria-label={texts.account.nav.signIn}>
      <Link className={styles.sessionLink} href="/giris">
        {texts.account.nav.signIn}
      </Link>
      <Link className={styles.sessionLink} href="/kayit">
        {texts.account.nav.register}
      </Link>
    </nav>
  );
}

/** Oturum durumunu okur; `api`'ye ulaşılamazsa `null` (başlık nötr kalır). */
async function readSession(): Promise<SessionState | null> {
  try {
    return await currentSession();
  } catch {
    return null;
  }
}

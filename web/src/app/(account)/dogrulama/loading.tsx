import { texts } from "@/lib/texts";
import styles from "../account.module.css";

/** Doğrulama sayfasının yükleniyor iskeleti (AGENTS.md §12.5). */
export default function VerificationLoading() {
  return (
    <main className={styles.page} aria-busy="true" aria-label={texts.account.myAccount.loading}>
      <div className={styles.panel}>
        <div className={styles.skeleton} />
        <div className={`${styles.skeleton} ${styles.skeletonTall}`} />
      </div>
    </main>
  );
}

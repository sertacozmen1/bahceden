import styles from "./loading.module.css";
import { texts } from "@/lib/texts";

/**
 * Yükleniyor durumu: içeriğin yerini tutan iskelet (AGENTS.md §12.5).
 *
 * Boş beyaz sayfa yasak; ayrıca iskelet sayfanın düzenini koruduğu için içerik
 * geldiğinde sıçrama olmaz.
 */
export default function Loading() {
  return (
    <main className={styles.page} aria-busy="true" aria-label={texts.storefront.loading}>
      <div className={styles.headLine} />
      <div className={styles.headLineShort} />
      <ul className={styles.list}>
        {Array.from({ length: 6 }, (_, index) => (
          <li key={index} className={styles.card} />
        ))}
      </ul>
    </main>
  );
}

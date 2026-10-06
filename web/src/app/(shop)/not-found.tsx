import Link from "next/link";
import type { Metadata } from "next";
import { texts } from "@/lib/texts";
import styles from "./error.module.css";

/**
 * Bulunamadı durumu (AGENTS.md §12.5): kullanıcı çıkmaz sokakta bırakılmaz,
 * vitrine dönüş yolu gösterilir.
 */
export const metadata: Metadata = {
  title: texts.notFound.title,
};

export default function NotFound() {
  return (
    <main className={styles.page}>
      <section className={styles.box}>
        <h1 className={styles.title}>{texts.notFound.title}</h1>
        <p className={styles.body}>{texts.notFound.body}</p>
        <Link className={styles.retry} href="/">
          {texts.notFound.backToStorefront}
        </Link>
      </section>
    </main>
  );
}

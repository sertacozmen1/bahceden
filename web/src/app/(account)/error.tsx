"use client";

import { texts } from "@/lib/texts";
import styles from "./account.module.css";

/**
 * Hesap sayfalarının hata durumu (AGENTS.md §12.5): ne oldu + ne yapmalı +
 * yeniden dene. Teknik ayrıntı gösterilmez ve konsola yazılmaz (K6, K10).
 */
export default function AccountError({ reset }: { readonly reset: () => void }) {
  return (
    <main className={styles.page}>
      <section className={styles.panel} role="alert">
        <h1 className={styles.title}>{texts.account.errors.title}</h1>
        <p className={styles.intro}>{texts.account.errors.body}</p>
        <button type="button" className={styles.action} onClick={reset}>
          {texts.account.errors.retry}
        </button>
      </section>
    </main>
  );
}

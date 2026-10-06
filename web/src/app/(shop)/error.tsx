"use client";

import { texts } from "@/lib/texts";
import styles from "./error.module.css";

/**
 * Hata durumu (AGENTS.md §12.5).
 *
 * Kullanıcıya iki şey söylenir: ne oldu ve ne yapabilir. Teknik ayrıntı
 * gösterilmez, konsola da yazılmaz (K6, K10): sunucu tarafı hatası Next.js
 * tarafından sunucu log'una düşer ve `digest` ile eşleştirilebilir.
 *
 * İstemci bileşeni olmak zorunda: `reset` ile yeniden denemeyi sunar.
 */
export default function StorefrontError({ reset }: { readonly reset: () => void }) {
  return (
    <main className={styles.page}>
      <section className={styles.box} role="alert">
        <h1 className={styles.title}>{texts.errors.title}</h1>
        <p className={styles.body}>{texts.errors.body}</p>
        <button type="button" className={styles.retry} onClick={reset}>
          {texts.errors.retry}
        </button>
      </section>
    </main>
  );
}

import Link from "next/link";
import { SessionNav } from "@/components/SessionNav";
import { texts } from "@/lib/texts";
import styles from "./AppShell.module.css";

/**
 * Uygulama kabuğu: her sayfanın paylaştığı üst başlık ve alt bilgi.
 *
 * Bilinçli olarak küçük: gezinme menüsü ve arama çubuğu yok, çünkü arkasında
 * duracak ekranlar henüz yok. Tek ek, oturum alanıdır ({@link SessionNav}):
 * giriş, kayıt ve hesap ekranları artık var. Olmayan bir şeye bağlanan boş
 * bir menü, kullanıcıyı çıkmaz sokağa sokar (AGENTS.md §12.5) ve yapılmamış
 * işi yapılmış gibi gösterir (K6).
 *
 * Marka alanı logo değil tipografidir: ikon seti ve görsel varlık bağımlılık
 * bütçesinde yok (ADR-002).
 */
export function AppShell({ children }: { readonly children: React.ReactNode }) {
  return (
    <>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link className={styles.brand} href="/">
            {texts.siteName}
          </Link>
          <p className={styles.tagline}>{texts.siteTagline}</p>
          <SessionNav />
        </div>
      </header>

      <div className={styles.content}>{children}</div>

      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <span>{texts.footer.copyright}</span>
          <span>{texts.footer.note}</span>
        </div>
      </footer>
    </>
  );
}

import Link from "next/link";
import { Sprout } from "lucide-react";
import { SessionNav } from "@/components/SessionNav";
import { texts } from "@/lib/texts";

/**
 * Uygulama kabuğu: her sayfanın paylaştığı üst başlık ve alt bilgi.
 *
 * Bilinçli olarak küçük: gezinme menüsü ve arama çubuğu yok, çünkü arkasında
 * duracak ekranlar henüz yok. Tek ek, oturum alanıdır ({@link SessionNav}).
 * Olmayan bir şeye bağlanan boş bir menü, kullanıcıyı çıkmaz sokağa sokar
 * (AGENTS.md §12.5) ve yapılmamış işi yapılmış gibi gösterir (K6).
 *
 * Marka işareti ikon setinden gelir (Lucide, ADR-027); elle çizilmiş logo
 * yok. Başlık 64px'i geçmez ve masaüstünde tek satırdır.
 */
export function AppShell({ children }: { readonly children: React.ReactNode }) {
  return (
    <>
      <header className="sticky top-0 z-40 border-b bg-background/85 backdrop-blur-md supports-[backdrop-filter]:bg-background/70">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-3 px-4 lg:px-6">
          <Link
            href="/"
            className="flex items-center gap-2 rounded-lg text-lg font-semibold tracking-tight text-foreground"
          >
            <span className="grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground">
              <Sprout aria-hidden className="size-4.5" />
            </span>
            {texts.siteName}
          </Link>
          <p className="hidden text-sm text-muted-foreground lg:block">{texts.siteTagline}</p>
          <SessionNav />
        </div>
      </header>

      <div className="flex-1">{children}</div>

      <footer className="mt-16 border-t">
        <div className="mx-auto flex max-w-6xl flex-col gap-1 px-4 py-6 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between lg:px-6">
          <span className="font-medium text-foreground">{texts.footer.copyright}</span>
          <span>{texts.footer.note}</span>
        </div>
      </footer>
    </>
  );
}

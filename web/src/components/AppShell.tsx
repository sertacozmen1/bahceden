import Link from "next/link";
import { Sprout } from "lucide-react";
import { SessionNav } from "@/components/SessionNav";
import { ThemeMenu } from "@/components/ThemeMenu";
import { texts } from "@/lib/texts";
import type { Theme } from "@/lib/theme";

/**
 * Uygulama kabuğu: her sayfanın paylaştığı üst başlık ve alt bilgi.
 *
 * Bilinçli olarak küçük: gezinme menüsü ve arama çubuğu yok, çünkü arkasında
 * duracak ekranlar henüz yok. Başlığın sağında oturum alanı
 * ({@link SessionNav}) ve hemen yanında tek ikonlu tema menüsü
 * ({@link ThemeMenu}, ADR-028) durur. Olmayan bir şeye bağlanan boş bir menü,
 * kullanıcıyı çıkmaz sokağa sokar (AGENTS.md §12.5) ve yapılmamış işi
 * yapılmış gibi gösterir (K6).
 *
 * Marka işareti ikon setinden gelir (Lucide, ADR-027); elle çizilmiş logo
 * yok. Başlık 64px'i geçmez ve her genişlikte tek satırdır: 375px'de
 * marka + iki oturum düğmesi + tema ikonu sığsın diye mobilde yan boşluk ve
 * aralıklar daraltılır.
 */
export function AppShell({ children, theme }: { readonly children: React.ReactNode; readonly theme: Theme }) {
  return (
    <>
      <header className="sticky top-0 z-40 border-b bg-background/85 backdrop-blur-md supports-[backdrop-filter]:bg-background/70">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-2 px-3 sm:gap-3 sm:px-4 lg:px-6">
          <Link
            href="/"
            className="flex shrink-0 items-center gap-2 rounded-lg text-lg font-semibold tracking-tight text-foreground"
          >
            <span className="grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground">
              <Sprout aria-hidden className="size-4.5" />
            </span>
            {texts.siteName}
          </Link>
          <p className="hidden text-sm text-muted-foreground lg:block">{texts.siteTagline}</p>
          <div className="ml-auto flex items-center gap-1 sm:gap-2">
            <SessionNav />
            <ThemeMenu initialTheme={theme} />
          </div>
        </div>
      </header>

      <div className="flex-1">{children}</div>

      <footer className="mt-16 border-t">
        <div className="mx-auto max-w-6xl px-4 py-6 text-sm lg:px-6">
          <p className="font-medium text-foreground">{texts.footer.copyright}</p>
        </div>
      </footer>
    </>
  );
}

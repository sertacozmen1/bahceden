import Link from "next/link";
import { useTranslations } from "next-intl";
import { BrandLogo, BrandMark } from "@/components/BrandLogo";
import { LocaleMenu } from "@/components/LocaleMenu";
import { SessionNav } from "@/components/SessionNav";
import { ThemeMenu } from "@/components/ThemeMenu";
import type { Theme } from "@/lib/theme";

/**
 * Uygulama kabuğu: her sayfanın paylaştığı üst başlık ve alt bilgi.
 *
 * Bilinçli olarak küçük: gezinme menüsü ve arama çubuğu yok, çünkü arkasında
 * duracak ekranlar henüz yok. Başlığın sağında oturum alanı
 * ({@link SessionNav}), yanında tek ikonlu dil ({@link LocaleMenu}, ADR-030)
 * ve tema ({@link ThemeMenu}, ADR-028) menüleri durur. Olmayan bir şeye
 * bağlanan boş bir menü, kullanıcıyı çıkmaz sokağa sokar (AGENTS.md §12.5) ve
 * yapılmamış işi yapılmış gibi gösterir (K6).
 *
 * Logo marka paketinden gelir ({@link BrandLogo}). Başlık 64px'i geçmez ve
 * her genişlikte tek satırdır: 375px'de logo + iki oturum düğmesi + iki ikon
 * sığsın diye 420px altında kelime işareti düşer, yalnızca "b" karosu kalır
 * ({@link BrandMark}); yan boşluk ve aralıklar daraltılır.
 */
export function AppShell({ children, theme }: { readonly children: React.ReactNode; readonly theme: Theme }) {
  const t = useTranslations();
  return (
    <>
      <header className="sticky top-0 z-40 border-b bg-background/85 backdrop-blur-md supports-[backdrop-filter]:bg-background/70">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-2 px-3 sm:gap-3 sm:px-4 lg:px-6">
          <Link href="/" className="flex shrink-0 items-center rounded-lg">
            <BrandLogo label={t("site.name")} className="max-[420px]:hidden" />
            <BrandMark label={t("site.name")} className="min-[421px]:hidden" />
          </Link>
          <p className="hidden text-sm text-muted-foreground lg:block">{t("site.tagline")}</p>
          <div className="ml-auto flex items-center gap-1 sm:gap-2">
            <SessionNav />
            <LocaleMenu />
            <ThemeMenu initialTheme={theme} />
          </div>
        </div>
      </header>

      <div className="flex-1">{children}</div>

      <footer className="mt-16 border-t">
        <div className="mx-auto max-w-6xl px-4 py-6 text-sm lg:px-6">
          <p className="font-medium text-foreground">{t("footer.copyright")}</p>
        </div>
      </footer>
    </>
  );
}

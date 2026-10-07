import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { cookies } from "next/headers";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getTranslations } from "next-intl/server";
import { AppShell } from "@/components/AppShell";
import { parseTheme, THEME_COOKIE } from "@/lib/theme";
import "./globals.css";

/**
 * Kök düzen.
 *
 * Dil istek başına seçilir (çerez → tarayıcı dili → Türkçe; ADR-030) ve
 * `<html lang>`'a yazılır: yanlış `lang` değeri ekran okuyucuyu yanıltır.
 * `NextIntlClientProvider` aynı dili ve mesajları istemci bileşenlerine geçirir
 * (ADR-029).
 *
 * Yazı tipi Geist (ADR-027): `next/font` derleme sırasında indirir ve kendi
 * sunucumuzdan verir; tarayıcı çalışma anında Google'a gitmez. `latin-ext`
 * alt kümesi Türkçe harfler (ç, ğ, ı, İ, ş) için zorunludur.
 */
const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin", "latin-ext"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin", "latin-ext"] });

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("site");
  return {
    title: {
      default: t("name"),
      template: `%s · ${t("name")}`,
    },
    description: t("tagline"),
  };
}

/**
 * Tarayıcı arayüzü (mobil adres çubuğu) marka koyu yeşiliyle boyanır; değer
 * `manifest.webmanifest` içindeki `theme_color` ile aynıdır. Sekme ikonu,
 * `apple-icon` ve manifest bu dizinde Next.js dosya kuralıyla durur.
 */
export const viewport: Viewport = { themeColor: "#24302a" };

/**
 * Tema tercihi çerezden sunucuda okunur ve `data-theme` olarak basılır
 * (ADR-028): ilk boyama doğru temayla gelir, sonradan düzelten betik yok.
 */
export default async function RootLayout({ children }: { readonly children: React.ReactNode }) {
  const theme = parseTheme((await cookies()).get(THEME_COOKIE)?.value);
  const locale = await getLocale();
  return (
    <html lang={locale} data-theme={theme} className={`${geistSans.variable} ${geistMono.variable}`}>
      <body className="flex min-h-dvh flex-col">
        <NextIntlClientProvider>
          <AppShell theme={theme}>{children}</AppShell>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

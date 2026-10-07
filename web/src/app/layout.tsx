import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { AppShell } from "@/components/AppShell";
import { texts } from "@/lib/texts";
import "./globals.css";

/**
 * Kök düzen.
 *
 * Dil `tr` olarak sabittir: çoklu dil bugün kapsam dışıdır (KALANLAR A4) ve
 * yanlış `lang` değeri ekran okuyucuyu yanıltır.
 *
 * Yazı tipi Geist (ADR-027): `next/font` derleme sırasında indirir ve kendi
 * sunucumuzdan verir; tarayıcı çalışma anında Google'a gitmez. `latin-ext`
 * alt kümesi Türkçe harfler (ç, ğ, ı, İ, ş) için zorunludur.
 */
const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin", "latin-ext"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin", "latin-ext"] });

export const metadata: Metadata = {
  title: {
    default: texts.siteName,
    template: `%s · ${texts.siteName}`,
  },
  description: texts.siteTagline,
};

export default function RootLayout({ children }: { readonly children: React.ReactNode }) {
  return (
    <html lang="tr" className={`${geistSans.variable} ${geistMono.variable}`}>
      <body className="flex min-h-dvh flex-col">
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}

import type { Metadata } from "next";
import { AppShell } from "@/components/AppShell";
import { texts } from "@/lib/texts";
import "./globals.css";

/**
 * Kök düzen.
 *
 * Dil `tr` olarak sabittir: çoklu dil bugün kapsam dışıdır (KALANLAR A4) ve
 * yanlış `lang` değeri ekran okuyucuyu yanıltır.
 */
export const metadata: Metadata = {
  title: {
    default: texts.siteName,
    template: `%s · ${texts.siteName}`,
  },
  description: texts.siteTagline,
};

export default function RootLayout({ children }: { readonly children: React.ReactNode }) {
  return (
    <html lang="tr">
      <body>
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}

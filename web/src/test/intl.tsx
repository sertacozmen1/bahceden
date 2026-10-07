import { render, type RenderResult } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactElement } from "react";
import type { Locale } from "@/i18n/locale";
import { messagesFor } from "@/i18n/messages";

/**
 * Testlerde dil (ADR-029).
 *
 * Bileşenler gerçek mesaj dosyalarıyla render edilir: testler kullanıcının
 * gördüğü metinle sorgular, anahtar adıyla değil. `testLocale` sunucu
 * tarafı taklidinin (`setup.ts`) de okuduğu dildir; her testten sonra
 * Türkçeye döner.
 */
export const testLocale: { current: Locale } = { current: "tr" };

export function renderWithIntl(ui: ReactElement, locale: Locale = "tr"): RenderResult {
  testLocale.current = locale;
  return render(
    <NextIntlClientProvider locale={locale} messages={messagesFor(locale)} timeZone="Europe/Istanbul">
      {ui}
    </NextIntlClientProvider>,
  );
}

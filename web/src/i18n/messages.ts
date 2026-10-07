import en from "../../messages/en.json";
import tr from "../../messages/tr.json";
import type { Locale } from "@/i18n/locale";

/**
 * Dil başına mesaj kataloğu (ADR-029).
 *
 * Kaynak dil Türkçedir: anahtarların tipi `tr.json`'dan türetilir
 * (`next-intl.d.ts`). `en.json` aynı yapıyı taşımak zorundadır; tip burada
 * denetlenir, anahtar kümesinin birebir eşitliği ayrıca testle sabittir.
 */
export type Messages = typeof tr;

const catalogs: Record<Locale, Messages> = { tr, en };

export function messagesFor(locale: Locale): Messages {
  return catalogs[locale];
}

import type { Locale } from "@/i18n/locale";

/**
 * Tarih ve saat gösterimi (AGENTS.md §12.3): tek yerde, `Intl` ile.
 *
 * Sunucu anları UTC ve ISO-8601 olarak gönderir; kullanıcıya gösterim zaman
 * dilimi `web`'in işidir (§11.3). MVP tek bölgededir (Türkiye), bu yüzden
 * gösterim her dilde Türkiye saatiyle yapılır.
 */

const DISPLAY_TIME_ZONE = "Europe/Istanbul";

const formatterCache = new Map<Locale, Intl.DateTimeFormat>();

/** Örnek: `2026-10-08T09:30:00Z` → `8 Eki 2026 12:30` (tr), `Oct 8, 2026, 12:30 PM` (en) */
export function formatDateTime(isoInstant: string, locale: Locale): string {
  let formatter = formatterCache.get(locale);
  if (formatter === undefined) {
    formatter = new Intl.DateTimeFormat(locale, {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: DISPLAY_TIME_ZONE,
    });
    formatterCache.set(locale, formatter);
  }
  return formatter.format(new Date(isoInstant));
}

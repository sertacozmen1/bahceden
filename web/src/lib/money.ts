import type { components } from "@contract";
import type { Locale } from "@/i18n/locale";

/**
 * Para gösterimi (ADR-006).
 *
 * Sunucu parayı kuruş cinsinden tamsayı olarak gönderir; burada yalnızca
 * BİÇİMLENDİRİLİR. İstemci para hesabı yapmaz: toplam, komisyon ve kargo
 * ücretinin tek otoritesi `api`'dir.
 *
 * Biçimlendirme tek yerde toplanır; bileşen içinde elle kuruş bölme veya
 * ondalık ayırıcı yazma yasaktır (AGENTS.md §12.3). Ayırıcılar seçili dile
 * göre değişir (ADR-029); para birimi simgesi her dilde dar biçimdedir (`₺`),
 * çünkü İngilizce varsayılan `TRY 42.50` alışveriş ekranında yabancı durur.
 */

type Money = components["schemas"]["Money"];

const MINOR_UNITS_PER_MAJOR = 100;

const formatterCache = new Map<string, Intl.NumberFormat>();

function formatterFor(locale: Locale, currency: string): Intl.NumberFormat {
  const key = `${locale}|${currency}`;
  const cached = formatterCache.get(key);
  if (cached) {
    return cached;
  }
  const formatter = new Intl.NumberFormat(locale, {
    style: "currency",
    currency,
    currencyDisplay: "narrowSymbol",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  formatterCache.set(key, formatter);
  return formatter;
}

/** Örnek: `{ amount: 12550, currency: "TRY" }` → `₺125,50` (tr), `₺125.50` (en) */
export function formatMoney(money: Money, locale: Locale): string {
  return formatterFor(locale, money.currency).format(money.amount / MINOR_UNITS_PER_MAJOR);
}

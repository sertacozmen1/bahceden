import type { components } from "@contract";

/**
 * Para gösterimi (ADR-006).
 *
 * Sunucu parayı kuruş cinsinden tamsayı olarak gönderir; burada yalnızca
 * BİÇİMLENDİRİLİR. İstemci para hesabı yapmaz: toplam, komisyon ve kargo
 * ücretinin tek otoritesi `api`'dir.
 *
 * Biçimlendirme tek yerde toplanır; bileşen içinde elle kuruş bölme veya
 * ondalık ayırıcı yazma yasaktır (AGENTS.md §12.3).
 */

type Money = components["schemas"]["Money"];

const MINOR_UNITS_PER_MAJOR = 100;

const formatterCache = new Map<string, Intl.NumberFormat>();

function formatterFor(currency: string): Intl.NumberFormat {
  const cached = formatterCache.get(currency);
  if (cached) {
    return cached;
  }
  const formatter = new Intl.NumberFormat("tr-TR", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  formatterCache.set(currency, formatter);
  return formatter;
}

/** Örnek: `{ amount: 12550, currency: "TRY" }` → `₺125,50` */
export function formatMoney(money: Money): string {
  return formatterFor(money.currency).format(money.amount / MINOR_UNITS_PER_MAJOR);
}

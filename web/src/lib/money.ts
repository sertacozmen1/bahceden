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

/**
 * Satıcının yazdığı TL tutarını kuruşa çevirir (add-product-listings varsayımı).
 * Bu bir hesap değil, girdi ayrıştırmadır; otorite yine `api`'dir ve tamsayı
 * kuruş alır (ADR-006). Kabul edilenler: `24`, `24,5`, `24,50`, `24.50`,
 * `1.250,50`, `1,250.50`, `₺24,50`. Son ayraçtan sonra 1–2 hane varsa kuruştur;
 * 3 hane varsa binlik ayracıdır. Geçersiz girdi `undefined` döner.
 */
export function parseMinorUnits(input: string): number | undefined {
  const cleaned = input.replace(/[\s₺]|TL/gi, "");
  if (!/^\d+([.,]\d+)*$/.test(cleaned)) {
    return undefined;
  }
  const lastSeparator = Math.max(cleaned.lastIndexOf(","), cleaned.lastIndexOf("."));
  const fractionLength = lastSeparator < 0 ? 0 : cleaned.length - lastSeparator - 1;
  const hasFraction = fractionLength === 1 || fractionLength === 2;
  const integerPart = (hasFraction ? cleaned.slice(0, lastSeparator) : cleaned).replace(/[.,]/g, "");
  const groups = (hasFraction ? cleaned.slice(0, lastSeparator) : cleaned).split(/[.,]/);
  // Binlik gruplar ilk grup dışında tam 3 hane olmalı: "12.34.567" geçersiz.
  if (groups.slice(1).some((group) => group.length !== 3)) {
    return undefined;
  }
  const fraction = hasFraction ? cleaned.slice(lastSeparator + 1).padEnd(2, "0") : "00";
  const minor = Number(integerPart) * MINOR_UNITS_PER_MAJOR + Number(fraction);
  return Number.isSafeInteger(minor) ? minor : undefined;
}

/** Formdaki başlangıç değeri: kuruş → seçili dilde, binlik ayracı olmadan (`2450` → `24,50` / `24.50`). */
export function toPriceInput(amount: number, locale: Locale): string {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    useGrouping: false,
  }).format(amount / MINOR_UNITS_PER_MAJOR);
}

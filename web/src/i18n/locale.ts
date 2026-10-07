/**
 * Dil seçimi: desteklenen diller ve seçim sırası (add-i18n D2, ADR-030).
 *
 * Dil URL'de görünmez. Sıra: kullanıcının açık seçimi (`bahceden_locale`
 * çerezi) → tarayıcının `Accept-Language` başlığı → Türkçe. Dili seçen tek
 * yer burasıdır; next-intl istek yapılandırması ve testler bunu kullanır.
 * Seçilen dil `api`'ye de aynı başlıkla iletilir (`lib/apiClient.ts`), iki
 * taraf aynı dili konuşur.
 */

export const LOCALES = ["tr", "en"] as const;

export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "tr";

export const LOCALE_COOKIE = "bahceden_locale";

/** Bir yıl: tercih cihazda kalıcıdır, oturumla ilgisi yoktur. */
const LOCALE_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

/** Bölge yok sayılır (`en-GB` → `en`); tanınmayan değer `undefined`. */
export function parseLocale(value: string | undefined | null): Locale | undefined {
  const language = value?.trim().toLowerCase().split(/[-_]/)[0];
  return LOCALES.find((locale) => locale === language);
}

/**
 * `Accept-Language` başlığından desteklenen en yüksek ağırlıklı dili seçer
 * (RFC 9110 §12.5.4). Ağırlığı 0 olan dil kabul edilmez; eşit ağırlıkta
 * başlıktaki sıra korunur.
 */
export function negotiateLocale(acceptLanguage: string | null | undefined): Locale | undefined {
  if (!acceptLanguage) {
    return undefined;
  }
  const candidates = acceptLanguage
    .split(",")
    .map((part, index) => {
      const [tag = "", ...params] = part.split(";");
      const q = params.map((param) => param.trim()).find((param) => param.startsWith("q="));
      const weight = q === undefined ? 1 : Number(q.slice(2));
      return { locale: parseLocale(tag), weight: Number.isFinite(weight) ? weight : 0, index };
    })
    .filter((candidate) => candidate.locale !== undefined && candidate.weight > 0)
    .sort((a, b) => b.weight - a.weight || a.index - b.index);
  return candidates[0]?.locale;
}

/** Çerez → başlık → varsayılan. */
export function resolveLocale(cookieValue: string | undefined, acceptLanguage: string | null | undefined): Locale {
  return parseLocale(cookieValue) ?? negotiateLocale(acceptLanguage) ?? DEFAULT_LOCALE;
}

/** Tarayıcıda seçimi kaydeder; sunucu bir sonraki istekte bu dili seçer. Yalnızca istemcide çağrılır. */
export function saveLocale(locale: Locale): void {
  document.cookie = `${LOCALE_COOKIE}=${locale}; Path=/; Max-Age=${LOCALE_COOKIE_MAX_AGE_SECONDS}; SameSite=Lax`;
}

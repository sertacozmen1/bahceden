/**
 * Tema tercihi: açık, koyu veya sistem (ADR-028).
 *
 * Tercih bir çerezde durur ve kök düzen onu sunucuda okuyup `<html
 * data-theme>` olarak basar. Böylece sayfa ilk boyamada doğru temayla gelir;
 * istemci betiği temayı sonradan düzeltmez, renk sıçraması olmaz. Çerez
 * kişisel veri veya oturum bilgisi taşımaz; tarayıcı JavaScript'i yazabilsin
 * diye `HttpOnly` değildir.
 */

export const THEMES = ["light", "dark", "system"] as const;

export type Theme = (typeof THEMES)[number];

export const THEME_COOKIE = "bahceden_theme";

/** Bir yıl: tercih cihazda kalıcıdır, oturumla ilgisi yoktur. */
export const THEME_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

export const DEFAULT_THEME: Theme = "system";

/** Çerezden gelen değer güvenilmez: tanınmayan her değer varsayılana düşer. */
export function parseTheme(value: string | undefined): Theme {
  return THEMES.find((theme) => theme === value) ?? DEFAULT_THEME;
}

/**
 * Seçimi tarayıcıda uygular: `<html data-theme>` anında değişir, tercih
 * çereze yazılır ki sonraki istekte sunucu aynı temayı bassın. Yalnızca
 * istemcide çağrılır.
 */
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  document.cookie = `${THEME_COOKIE}=${theme}; Path=/; Max-Age=${THEME_COOKIE_MAX_AGE_SECONDS}; SameSite=Lax`;
}

/**
 * `web` içindeki BFF sözleşmesi: tarayıcıdaki formların konuştuğu kendi Route
 * Handler'larımız (design.md D6).
 *
 * Bu, `api` sözleşmesi DEĞİLDİR: `api` tipleri `@contract`'tan üretilir. Burada
 * yalnızca tarayıcı ile BFF arasındaki ince katman tanımlanır; token hiçbir
 * yanıtta yer almaz. Hata gövdesi `api` ile aynı şekildedir (`ApiError`), böylece
 * formlar tek bir hata çözümü kullanır.
 */

export const bffPaths = {
  register: "/api/auth/register",
  login: "/api/auth/login",
  challenge: "/api/auth/challenge",
  logout: "/api/auth/logout",
  verificationRequest: "/api/auth/verification-request",
  refresh: "/api/auth/refresh",
  passwordResetRequest: "/api/auth/password-reset-request",
  passwordReset: "/api/auth/password-reset",
  passwordChange: "/api/auth/password-change",
  // Yenileme çerezi gerekmez; `/api/auth` dışında (add-account-locale-update D2).
  accountLocale: "/api/account/locale",
} as const;

/** Girişin sonucu. Token taşımaz: token'lar yalnızca `HttpOnly` çerezde. */
export type BffLoginResponse =
  | { readonly outcome: "AUTHENTICATED" }
  | { readonly outcome: "CHALLENGE_REQUIRED"; readonly message: string };

/** Kayıt ve benzeri "alındı" yanıtları. */
export type BffMessageResponse = { readonly message: string };

/**
 * BFF'in kendi ürettiği hata kodları. `api` kodlarıyla çakışmaz; yalnızca
 * `api`'ye hiç ulaşılmadığında kullanılır.
 */
export const bffErrorCodes = {
  crossSite: "CROSS_SITE_REQUEST",
  unavailable: "SERVICE_UNAVAILABLE",
  invalidBody: "VALIDATION_FAILED",
} as const;

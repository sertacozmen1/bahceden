import type { components } from "@contract";
import { cache } from "react";
import { ApiRequestError, apiGet, apiSend } from "@/lib/apiClient";
import { readAccessToken } from "@/lib/session";

/**
 * Hesap ve oturum verisi (sunucu tarafı). `api`'nin hesap uçlarını sarar.
 *
 * Tipler sözleşmeden üretilir; elle yazılmaz (ADR-003). Token'lar burada
 * yalnızca Route Handler'lar ile çerezler arasında taşınır, tarayıcıya
 * dönmez (design.md D6).
 */

export type AccountView = components["schemas"]["AccountView"];
export type SessionTokens = components["schemas"]["SessionTokens"];
type LoginChallengeIssued = components["schemas"]["LoginChallengeIssued"];
type RegistrationAccepted = components["schemas"]["RegistrationAccepted"];

/** Girişin iki başarılı sonucu: `api` bunları durum koduyla ayırır (200 / 202). */
export type LoginResult =
  | { readonly kind: "authenticated"; readonly tokens: SessionTokens }
  | { readonly kind: "challenge"; readonly challenge: LoginChallengeIssued };

const HTTP_ACCEPTED = 202;
const HTTP_UNAUTHORIZED = 401;

export async function register(
  email: string,
  password: string,
  forwardedFor: string | null,
): Promise<RegistrationAccepted | undefined> {
  const result = await apiSend<RegistrationAccepted>("POST", "/api/v1/accounts", {
    body: { email, password },
    forwardedFor,
  });
  return result.data;
}

export async function login(email: string, password: string, forwardedFor: string | null): Promise<LoginResult> {
  const result = await apiSend<SessionTokens | LoginChallengeIssued>("POST", "/api/v1/sessions", {
    body: { email, password },
    forwardedFor,
  });
  const data = requireBody(result.data);
  // 200 token'ları, 202 ikinci adımı taşır; iki şema alanlarıyla da ayrışır.
  if (result.status !== HTTP_ACCEPTED && "accessToken" in data) {
    return { kind: "authenticated", tokens: data };
  }
  if ("expiresAt" in data) {
    return { kind: "challenge", challenge: data };
  }
  throw new Error("Sunucu girişte beklenmeyen bir yanıt döndürdü.");
}

export async function completeChallenge(
  email: string,
  code: string,
  forwardedFor: string | null,
): Promise<SessionTokens> {
  const result = await apiSend<SessionTokens>("POST", "/api/v1/sessions/challenge", {
    body: { email, code },
    forwardedFor,
  });
  return requireBody(result.data);
}

export async function refreshSession(refreshToken: string, forwardedFor: string | null): Promise<SessionTokens> {
  const result = await apiSend<SessionTokens>("POST", "/api/v1/sessions/refresh", {
    body: { refreshToken },
    forwardedFor,
  });
  return requireBody(result.data);
}

export async function logout(refreshToken: string): Promise<void> {
  await apiSend<never>("DELETE", "/api/v1/sessions/current", { body: { refreshToken } });
}

export async function verifyEmail(token: string, forwardedFor: string | null): Promise<void> {
  await apiSend<never>("POST", "/api/v1/accounts/verifications", { body: { token }, forwardedFor });
}

/** Erişim token'ı yoksa başlık gönderilmez; api 401 döner. */
export async function requestVerificationLink(accessToken: string | undefined): Promise<void> {
  await apiSend<never>("POST", "/api/v1/accounts/me/verification-requests", { accessToken });
}

/**
 * Oturum sahibinin durumu, istek başına bir kez okunur (design.md Open
 * Questions): düzen ve sayfa aynı isteği paylaşır (`cache`).
 *
 * - `anonymous`: oturum çerezi yok.
 * - `expired`: çerez var ama erişim token'ı geçmiş; yenileme gerekir.
 * - `signed-in`: güncel hesap bilgisi.
 */
export type SessionState =
  | { readonly kind: "anonymous" }
  | { readonly kind: "expired" }
  | { readonly kind: "signed-in"; readonly account: AccountView; readonly accessToken: string };

export const currentSession = cache(async (): Promise<SessionState> => {
  const accessToken = await readAccessToken();
  if (accessToken === undefined) {
    return { kind: "anonymous" };
  }
  try {
    const account = await apiGet<AccountView>("/api/v1/accounts/me", { accessToken });
    return { kind: "signed-in", account, accessToken };
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === HTTP_UNAUTHORIZED) {
      return { kind: "expired" };
    }
    throw error;
  }
});

function requireBody<T>(data: T | undefined): T {
  if (data === undefined) {
    throw new Error("Sunucu beklenen gövdeyi döndürmedi.");
  }
  return data;
}

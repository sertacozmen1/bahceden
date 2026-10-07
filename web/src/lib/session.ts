import type { components } from "@contract";
import { cookies } from "next/headers";

/**
 * Oturum çerezleri (ADR-008, design.md D6).
 *
 * Token'lar yalnızca `HttpOnly` çerezlerde durur; tarayıcı JavaScript'i
 * onlara erişemez. `localStorage`, `sessionStorage` ve istemci state'inde token
 * tutmak yasaktır (AGENTS.md §12.3). Bu modül yalnızca sunucu tarafında
 * (Route Handler, Server Component) çağrılır.
 *
 * İki çerez:
 * - `bahceden_at`: erişim token'ı, her sayfa isteğinde gider (`path=/`).
 * - `bahceden_rt`: yenileme token'ı, yalnızca `/api/auth` altında gider; sayfa
 *   isteklerinde sunucuya taşınmaz, yüzey daralır.
 *
 * Erişim çerezinin ömrü bilinçli olarak JWT'nin 15 dakikasından uzundur:
 * yenileme token'ı kadar yaşar. JWT dolduğunda çerez yine durur ve sunucu
 * "oturum var ama yenilenmeli" bilgisini buradan okur; çerez yoksa oturum da
 * yoktur. Süresi dolmuş JWT tek başına hiçbir şeye yetki vermez.
 */

type SessionTokens = components["schemas"]["SessionTokens"];

export const ACCESS_COOKIE = "bahceden_at";
export const REFRESH_COOKIE = "bahceden_rt";

/** Yenileme çerezinin gönderildiği tek yol: yenileme ve çıkış Route Handler'ları burada. */
export const AUTH_ROUTE_PREFIX = "/api/auth";

const MILLIS_PER_SECOND = 1000;

const baseCookie = {
  httpOnly: true,
  secure: true,
  sameSite: "lax",
} as const;

export async function writeSession(tokens: SessionTokens): Promise<void> {
  const jar = await cookies();
  const maxAge = secondsUntil(tokens.refreshTokenExpiresAt);
  jar.set(ACCESS_COOKIE, tokens.accessToken, { ...baseCookie, path: "/", maxAge });
  jar.set(REFRESH_COOKIE, tokens.refreshToken, { ...baseCookie, path: AUTH_ROUTE_PREFIX, maxAge });
}

export async function clearSession(): Promise<void> {
  const jar = await cookies();
  jar.delete({ name: ACCESS_COOKIE, path: "/" });
  jar.delete({ name: REFRESH_COOKIE, path: AUTH_ROUTE_PREFIX });
}

export async function readAccessToken(): Promise<string | undefined> {
  return (await cookies()).get(ACCESS_COOKIE)?.value;
}

/** Yalnızca `/api/auth` altındaki Route Handler'larda dolu gelir. */
export async function readRefreshToken(): Promise<string | undefined> {
  return (await cookies()).get(REFRESH_COOKIE)?.value;
}

function secondsUntil(isoInstant: string): number {
  return Math.max(0, Math.floor((Date.parse(isoInstant) - Date.now()) / MILLIS_PER_SECOND));
}

import { apiErrorResponse } from "@/features/account/routeSupport";
import { ApiRequestError } from "@/lib/apiError";

const HTTP_UNAUTHORIZED = 401;

/**
 * `api`'nin koruma başlıkları (add-seller-onboarding D6) tarayıcıya aynen
 * geçer: tür, boyut, dosya adı, `nosniff`, `sandbox` ve önbelleksizlik.
 * Başka hiçbir başlık iletilmez.
 */
const FORWARDED_HEADERS = [
  "content-type",
  "content-length",
  "content-disposition",
  "x-content-type-options",
  "content-security-policy",
  "cache-control",
] as const;

/**
 * Belgeyi `api`'den alıp tarayıcıya akış olarak iletir; içerik bellekte
 * tutulmaz. Sahibin ve admin'in belge Route Handler'ları bunu paylaşır (K9).
 *
 * Erişim token'ının süresi geçmişse kullanıcı bir kez yenilemeye gönderilir ve
 * aynı adrese döner (ADR-023); diğer hatalar ortak gövdeyle iletilir.
 */
export async function serveDocument(load: () => Promise<Response>, selfPath: string): Promise<Response> {
  try {
    const upstream = await load();
    const headers = new Headers();
    for (const name of FORWARDED_HEADERS) {
      const value = upstream.headers.get(name);
      if (value !== null) {
        headers.set(name, value);
      }
    }
    return new Response(upstream.body, { status: upstream.status, headers });
  } catch (error) {
    if (error instanceof ApiRequestError) {
      if (error.status === HTTP_UNAUTHORIZED) {
        return new Response(null, {
          status: 307,
          headers: { Location: `/api/auth/refresh?next=${encodeURIComponent(selfPath)}` },
        });
      }
      return apiErrorResponse(error);
    }
    throw error;
  }
}

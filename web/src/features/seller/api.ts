import type { components } from "@contract";
import { ApiRequestError, apiDownload, apiGet, apiSend } from "@/lib/apiClient";

/**
 * Satıcı başvurusu verisi (sunucu tarafı). `api`'nin `/sellers/me` uçlarını
 * sarar (seller-onboarding spec'i).
 *
 * Tipler sözleşmeden üretilir; elle yazılmaz (ADR-003). Erişim token'ı
 * çağırandan gelir: sayfada oturum kapısından, Route Handler'da çerezden.
 */
export type SellerSelfView = components["schemas"]["SellerSelfView"];
export type SellerStatus = SellerSelfView["status"];
export type SellerDocumentType = SellerSelfView["documentType"];

/** Formdaki belge türü seçenekleri; sözleşmedeki enum ile aynı sırada. */
export const sellerDocumentTypes = ["CKS_CERTIFICATE", "TAX_CERTIFICATE"] as const satisfies readonly SellerDocumentType[];

const HTTP_NOT_FOUND = 404;
const OWN_SELLER_PATH = "/api/v1/sellers/me";

/**
 * Oturum sahibinin satıcı kaydı. Kayıt yoksa `null`: `api`'nin 404'ü bir
 * hata değil, başvuruya davettir.
 */
export async function getOwnSeller(accessToken: string): Promise<SellerSelfView | null> {
  try {
    return await apiGet<SellerSelfView>(OWN_SELLER_PATH, { accessToken });
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === HTTP_NOT_FOUND) {
      return null;
    }
    throw error;
  }
}

/**
 * Başvuru (`POST`) veya reddedilen başvurunun yeniden gönderimi (`PUT`).
 * Form olduğu gibi `multipart/form-data` olarak iletilir (add-seller-onboarding D3).
 */
export async function submitSellerApplication(
  method: "POST" | "PUT",
  form: FormData,
  accessToken: string | undefined,
): Promise<{ readonly status: number; readonly seller: SellerSelfView }> {
  const result = await apiSend<SellerSelfView>(method, OWN_SELLER_PATH, { body: form, accessToken });
  if (result.data === undefined) {
    throw new Error("Sunucu başvuru yanıtında gövde döndürmedi.");
  }
  return { status: result.status, seller: result.data };
}

/** Güncel belgenin ham yanıtı; Route Handler onu tarayıcıya akış olarak iletir. */
export async function downloadOwnSellerDocument(accessToken: string | undefined): Promise<Response> {
  return apiDownload(`${OWN_SELLER_PATH}/document`, accessToken);
}

import type { components } from "@contract";
import { ApiRequestError, apiGet, apiSend } from "@/lib/apiClient";

/**
 * Satıcının ilanları (sunucu tarafı). `api`'nin `/sellers/me/products`
 * uçlarını sarar (product-listings spec'i). Tipler sözleşmeden üretilir (ADR-003).
 */
export type SellerListingView = components["schemas"]["SellerListingView"];
export type SellerListingPage = components["schemas"]["PageResponseSellerListingView"];
export type ListingRequest = components["schemas"]["ListingRequest"];
export type ListingStatus = SellerListingView["status"];
export type ProductUnit = SellerListingView["unit"];

/** Formdaki birim seçenekleri; sözleşmedeki enum ile aynı. */
export const productUnits = ["KILOGRAM", "PIECE", "BUNCH"] as const satisfies readonly ProductUnit[];

/** Kargoya verme süresi seçenekleri, iş günü (ADR-058); sınır `api`'dedir. */
export const dispatchDayOptions = [1, 2, 3] as const;

/** Süre gönderilmeyen yeni ilanın varsayılanı; `api` ile aynı (add-cart-and-checkout D3). */
export const defaultDispatchDays = 2;

const HTTP_NOT_FOUND = 404;
const OWN_LISTINGS_PATH = "/api/v1/sellers/me/products";

export async function listOwnListings(accessToken: string, page: number, size: number): Promise<SellerListingPage> {
  return apiGet<SellerListingPage>(OWN_LISTINGS_PATH, { accessToken, query: { page, size } });
}

/** Satıcının tek ilanı; yoksa veya başkasınaysa `null` (api 404). */
export async function getOwnListing(accessToken: string, id: number): Promise<SellerListingView | null> {
  try {
    return await apiGet<SellerListingView>(`${OWN_LISTINGS_PATH}/${id}`, { accessToken });
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === HTTP_NOT_FOUND) {
      return null;
    }
    throw error;
  }
}

/**
 * Gövde BFF'ten gelir ve `api`'ye olduğu gibi iletilir; alanların doğrulama
 * otoritesi `api`'dir (eksik veya yanlış tipli alan 400 + `fieldErrors`).
 */
export async function createListing(
  accessToken: string | undefined,
  body: Readonly<Record<string, unknown>>,
): Promise<{ readonly status: number; readonly listing: SellerListingView }> {
  const result = await apiSend<SellerListingView>("POST", OWN_LISTINGS_PATH, { accessToken, body });
  return { status: result.status, listing: requireBody(result.data) };
}

export async function updateListing(
  accessToken: string | undefined,
  id: number,
  body: Readonly<Record<string, unknown>>,
): Promise<SellerListingView> {
  const result = await apiSend<SellerListingView>("PUT", `${OWN_LISTINGS_PATH}/${id}`, { accessToken, body });
  return requireBody(result.data);
}

/** Satıcının yayın geçişleri; BFF bu listeyi beyaz liste olarak kullanır (D1). */
export const listingActions = ["publish", "unpublish", "archive", "restore"] as const;
export type ListingAction = (typeof listingActions)[number];

/** Tek fotoğraf yükleme; form `multipart/form-data` olarak iletilir (D3). */
export async function uploadListingPhoto(
  accessToken: string | undefined,
  id: number,
  form: FormData,
): Promise<SellerListingView> {
  const result = await apiSend<SellerListingView>("POST", `${OWN_LISTINGS_PATH}/${id}/photos`, { accessToken, body: form });
  return requireBody(result.data);
}

export async function deleteListingPhoto(
  accessToken: string | undefined,
  id: number,
  photoId: number,
): Promise<SellerListingView> {
  const result = await apiSend<SellerListingView>("DELETE", `${OWN_LISTINGS_PATH}/${id}/photos/${photoId}`, {
    accessToken,
  });
  return requireBody(result.data);
}

export async function reorderListingPhotos(
  accessToken: string | undefined,
  id: number,
  body: Readonly<Record<string, unknown>>,
): Promise<SellerListingView> {
  const result = await apiSend<SellerListingView>("PUT", `${OWN_LISTINGS_PATH}/${id}/photos/order`, { accessToken, body });
  return requireBody(result.data);
}

export async function transitionListing(
  accessToken: string | undefined,
  id: number,
  action: ListingAction,
): Promise<SellerListingView> {
  const result = await apiSend<SellerListingView>("POST", `${OWN_LISTINGS_PATH}/${id}/${action}`, { accessToken });
  return requireBody(result.data);
}

function requireBody<T>(data: T | undefined): T {
  if (data === undefined) {
    throw new Error("Sunucu beklenen gövdeyi döndürmedi.");
  }
  return data;
}

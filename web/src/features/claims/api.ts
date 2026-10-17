import type { components } from "@contract";
import { apiDownload, apiGet, apiSend } from "@/lib/apiClient";
import type { OrderDetail, SellerOrderDetail } from "@/features/orders/api";

/**
 * Ayıplı ürün talepleri (sunucu tarafı; defect-claims spec'i, ADR-081,
 * ADR-084). `api`'nin talep uçlarını sarar; tutarları ve süreleri sunucu
 * hesaplar, ekran gösterir.
 */
export type DefectClaimView = components["schemas"]["DefectClaimView"];
export type DefectClaimStatus = DefectClaimView["status"];
export type DefectClaimPhoto = DefectClaimView["photos"][number];
export type ClaimSettlementView = components["schemas"]["ClaimSettlementView"];
export type AdminClaimSummary = components["schemas"]["AdminClaimSummary"];
export type AdminClaimDetail = components["schemas"]["AdminClaimDetail"];
type AdminClaimPage = components["schemas"]["PageResponseAdminClaimSummary"];

/** Admin talep sekmeleri; karar bekleyenler ilk sırada (ADR-086). */
export const adminClaimStatuses = [
  "ESCALATED",
  "OPEN",
  "OFFERED",
  "REJECTED",
  "REFUNDED",
  "CLOSED",
] as const satisfies readonly DefectClaimStatus[];

/** Alıcının talep eylemleri; BFF bu listeyi beyaz liste olarak kullanır (ADR-080). */
export const buyerClaimActions = ["accept", "escalate"] as const;
export type BuyerClaimAction = (typeof buyerClaimActions)[number];

/** Fotoğraf boyutu; `api` yalnızca bu iki değeri kabul eder. */
export const claimPhotoSizes = ["CARD", "LARGE"] as const;
export type ClaimPhotoSize = (typeof claimPhotoSizes)[number];

/** Talepte en az ve en çok fotoğraf; sunucudaki sınırla aynı (erken uyarı için). */
export const MIN_CLAIM_PHOTOS = 1;
export const MAX_CLAIM_PHOTOS = 3;

const ORDERS_PATH = "/api/v1/orders";
const SELLER_ORDERS_PATH = "/api/v1/sellers/me/orders";
const ADMIN_CLAIMS_PATH = "/api/v1/admin/claims";

/** Talep açma; form `multipart/form-data` olarak olduğu gibi iletilir (ADR-081). */
export async function openClaim(
  accessToken: string | undefined,
  orderNumber: string,
  sellerOrderId: number,
  form: FormData,
): Promise<OrderDetail> {
  const path = `${ORDERS_PATH}/${encodeURIComponent(orderNumber)}/seller-orders/${sellerOrderId}/claim`;
  return requireBody((await apiSend<OrderDetail>("POST", path, { accessToken, body: form })).data);
}

/** Talep fotoğrafının ham yanıtı; koruma başlıklarıyla tarayıcıya akış olarak iletilir (ADR-084). */
export async function downloadClaimPhoto(
  accessToken: string | undefined,
  claimId: number,
  position: number,
  size: ClaimPhotoSize,
): Promise<Response> {
  return apiDownload(`/api/v1/claims/${claimId}/photos/${position}?size=${size}`, accessToken);
}

/** Satıcının yanıtı: kabul, adetli teklif veya gerekçeli ret (ADR-080). */
export async function respondToClaim(
  accessToken: string | undefined,
  sellerOrderId: number,
  body: Readonly<Record<string, unknown>>,
): Promise<SellerOrderDetail> {
  return requireBody(
    (await apiSend<SellerOrderDetail>("POST", `${SELLER_ORDERS_PATH}/${sellerOrderId}/claim/response`, { accessToken, body }))
      .data,
  );
}

/** Yanıtın para etkisi; yan etkisizdir (ADR-086). */
export async function previewClaim(
  accessToken: string | undefined,
  sellerOrderId: number,
  body: Readonly<Record<string, unknown>>,
): Promise<ClaimSettlementView> {
  return requireBody(
    (await apiSend<ClaimSettlementView>("POST", `${SELLER_ORDERS_PATH}/${sellerOrderId}/claim/preview`, { accessToken, body }))
      .data,
  );
}

/** Alıcının teklifi veya reddi kabul etmesi ya da yönetime taşıması (ADR-080). */
export async function buyerClaimAction(
  accessToken: string | undefined,
  orderNumber: string,
  sellerOrderId: number,
  action: BuyerClaimAction,
): Promise<OrderDetail> {
  const path = `${ORDERS_PATH}/${encodeURIComponent(orderNumber)}/seller-orders/${sellerOrderId}/claim/${action}`;
  return requireBody((await apiSend<OrderDetail>("POST", path, { accessToken })).data);
}

export async function listClaimsForAdmin(
  accessToken: string,
  status: DefectClaimStatus,
  page: number,
  size: number,
): Promise<AdminClaimPage> {
  return apiGet<AdminClaimPage>(ADMIN_CLAIMS_PATH, { accessToken, query: { status, page, size } });
}

export async function getClaimForAdmin(accessToken: string, id: number): Promise<AdminClaimDetail> {
  return apiGet<AdminClaimDetail>(`${ADMIN_CLAIMS_PATH}/${id}`, { accessToken });
}

/** Admin kararının para etkisi; yan etkisizdir. */
export async function previewClaimForAdmin(
  accessToken: string | undefined,
  id: number,
  body: Readonly<Record<string, unknown>>,
): Promise<ClaimSettlementView> {
  return requireBody((await apiSend<ClaimSettlementView>("POST", `${ADMIN_CLAIMS_PATH}/${id}/preview`, { accessToken, body })).data);
}

/** Admin kararı: satır başına adetle iade ya da gerekçeli ret (ADR-080). */
export async function decideClaim(
  accessToken: string | undefined,
  id: number,
  body: Readonly<Record<string, unknown>>,
): Promise<AdminClaimDetail> {
  return requireBody((await apiSend<AdminClaimDetail>("POST", `${ADMIN_CLAIMS_PATH}/${id}/decision`, { accessToken, body })).data);
}

function requireBody<T>(data: T | undefined): T {
  if (data === undefined) {
    throw new Error("Sunucu beklenen gövdeyi döndürmedi.");
  }
  return data;
}

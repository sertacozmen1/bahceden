import type { components } from "@contract";
import { apiDownload, apiGet, apiSend } from "@/lib/apiClient";

/**
 * Admin: satıcı başvuruları (sunucu tarafı). `api`'nin `/admin/sellers`
 * uçlarını sarar (seller-onboarding spec'i "Admin başvuruları karara bağlar").
 *
 * Tipler sözleşmeden üretilir (ADR-003). Erişim token'ı çağırandan gelir;
 * rol denetiminin otoritesi `api`'dir.
 */
export type AdminSellerSummary = components["schemas"]["AdminSellerSummary"];
export type AdminSellerDetail = components["schemas"]["AdminSellerDetail"];
export type AdminSellerPage = components["schemas"]["PageResponseAdminSellerSummary"];
export type SellerStatus = AdminSellerSummary["status"];

/** Liste sekmeleri; bekleyenler ilk sırada, çünkü admin'in asıl işi onlar. */
export const adminSellerStatuses = ["PENDING", "APPROVED", "REJECTED", "SUSPENDED"] as const satisfies readonly SellerStatus[];

/**
 * Karar eylemleri. Ret ve askı gerekçe ister; liste BFF'te beyaz liste
 * olarak da kullanılır.
 */
export const sellerDecisions = ["approve", "reject", "suspend", "reinstate"] as const;
export type SellerDecision = (typeof sellerDecisions)[number];

export const decisionsWithReason: ReadonlySet<SellerDecision> = new Set(["reject", "suspend"]);

const ADMIN_SELLERS_PATH = "/api/v1/admin/sellers";

export async function listSellersForAdmin(
  accessToken: string,
  status: SellerStatus,
  page: number,
  size: number,
): Promise<AdminSellerPage> {
  return apiGet<AdminSellerPage>(ADMIN_SELLERS_PATH, { accessToken, query: { status, page, size } });
}

export async function getSellerForAdmin(accessToken: string, id: number): Promise<AdminSellerDetail> {
  return apiGet<AdminSellerDetail>(`${ADMIN_SELLERS_PATH}/${id}`, { accessToken });
}

/** Karar verir; gerekçe yalnızca ret ve askıda gönderilir. Güncel kaydı döner. */
export async function decideSeller(
  accessToken: string | undefined,
  id: number,
  decision: SellerDecision,
  reason: string | undefined,
): Promise<AdminSellerDetail | undefined> {
  const result = await apiSend<AdminSellerDetail>("POST", `${ADMIN_SELLERS_PATH}/${id}/${decision}`, {
    accessToken,
    body: decisionsWithReason.has(decision) ? { reason: reason ?? "" } : undefined,
  });
  return result.data;
}

/** Güncel belgenin ham yanıtı; Route Handler onu tarayıcıya akış olarak iletir. */
export async function downloadSellerDocumentForAdmin(accessToken: string | undefined, id: number): Promise<Response> {
  return apiDownload(`${ADMIN_SELLERS_PATH}/${id}/document`, accessToken);
}

export type AdminProductSummary = components["schemas"]["AdminProductSummary"];
export type AdminProductPage = components["schemas"]["PageResponseAdminProductSummary"];

/** Admin ilan sekmeleri (add-product-listings D7). */
export const adminProductStatuses = ["PUBLISHED", "REMOVED"] as const satisfies readonly AdminProductSummary["status"][];
export type AdminProductStatus = (typeof adminProductStatuses)[number];

/** İlan denetim eylemleri; BFF beyaz listesi. */
export const productDecisions = ["remove", "reinstate"] as const;
export type ProductDecision = (typeof productDecisions)[number];

const ADMIN_PRODUCTS_PATH = "/api/v1/admin/products";

export async function listProductsForAdmin(
  accessToken: string,
  status: AdminProductStatus,
  page: number,
  size: number,
): Promise<AdminProductPage> {
  return apiGet<AdminProductPage>(ADMIN_PRODUCTS_PATH, { accessToken, query: { status, page, size } });
}

/** Kaldırma gerekçe ister; yeniden açma gövdesizdir. */
export async function decideProduct(
  accessToken: string | undefined,
  id: number,
  decision: ProductDecision,
  reason: string,
): Promise<AdminProductSummary | undefined> {
  const result = await apiSend<AdminProductSummary>("POST", `${ADMIN_PRODUCTS_PATH}/${id}/${decision}`, {
    accessToken,
    body: decision === "remove" ? { reason } : undefined,
  });
  return result.data;
}

export type AdminSellerOrderSummary = components["schemas"]["AdminSellerOrderSummary"];
export type AdminSellerOrderDetail = components["schemas"]["AdminSellerOrderDetail"];
export type AdminSellerOrderPage = components["schemas"]["PageResponseAdminSellerOrderSummary"];

/** Teslim edilemeyen sipariş sekmeleri; karar bekleyenler ilk sırada (add-shipments D7). */
export const adminSellerOrderStatuses = ["DELIVERY_FAILED", "REFUNDED", "CLOSED"] as const satisfies readonly AdminSellerOrderSummary["status"][];
export type AdminSellerOrderStatus = (typeof adminSellerOrderStatuses)[number];

/** Admin kararları; BFF beyaz listesi. */
export const undeliveredDecisions = ["REFUND", "CLOSE"] as const satisfies readonly components["schemas"]["UndeliveredResolutionRequest"]["decision"][];
export type UndeliveredDecision = (typeof undeliveredDecisions)[number];

const ADMIN_SELLER_ORDERS_PATH = "/api/v1/admin/seller-orders";

export async function listUndeliveredForAdmin(
  accessToken: string,
  status: AdminSellerOrderStatus,
  page: number,
  size: number,
): Promise<AdminSellerOrderPage> {
  return apiGet<AdminSellerOrderPage>(ADMIN_SELLER_ORDERS_PATH, { accessToken, query: { status, page, size } });
}

export async function getUndeliveredForAdmin(accessToken: string, id: number): Promise<AdminSellerOrderDetail> {
  return apiGet<AdminSellerOrderDetail>(`${ADMIN_SELLER_ORDERS_PATH}/${id}`, { accessToken });
}

/** İade veya gerekçeyle kapatma; güncel kaydı döner. Gerekçe kapatmada zorunludur, `api` denetler. */
export async function resolveUndelivered(
  accessToken: string | undefined,
  id: number,
  decision: UndeliveredDecision,
  reason: string,
): Promise<AdminSellerOrderDetail | undefined> {
  const result = await apiSend<AdminSellerOrderDetail>("POST", `${ADMIN_SELLER_ORDERS_PATH}/${id}/resolution`, {
    accessToken,
    body: { decision, reason: reason === "" ? null : reason },
  });
  return result.data;
}

import type { components } from "@contract";
import { ApiRequestError, apiGet, apiSend } from "@/lib/apiClient";

/**
 * Siparişler (sunucu tarafı). `api`'nin `/orders` ve `/sellers/me/orders`
 * uçlarını sarar (cart-and-checkout ve orders spec'leri, add-cart-and-checkout
 * D6, D8). Tutarları sunucu hesaplar; ekran yalnızca gösterir.
 */
export type OrderPlaced = components["schemas"]["OrderPlaced"];
export type OrderDetail = components["schemas"]["OrderDetail"];
export type OrderSummary = components["schemas"]["OrderSummary"];
export type OrderStatus = OrderDetail["status"];
export type BuyerSellerOrderView = components["schemas"]["BuyerSellerOrderView"];
export type SellerOrderStatus = BuyerSellerOrderView["status"];
export type SellerOrderSummary = components["schemas"]["SellerOrderSummary"];
export type SellerOrderDetail = components["schemas"]["SellerOrderDetail"];
type OrderPage = components["schemas"]["PageResponseOrderSummary"];
type SellerOrderPage = components["schemas"]["PageResponseSellerOrderSummary"];

/** Satıcı listesindeki süzgeç seçenekleri; `api`'nin kabul ettiği durumlar. */
export const sellerOrderFilters = ["NEW", "CANCELLED"] as const satisfies readonly SellerOrderStatus[];

const HTTP_NOT_FOUND = 404;
const ORDERS_PATH = "/api/v1/orders";
const SELLER_ORDERS_PATH = "/api/v1/sellers/me/orders";

/** Sepetten sipariş; aynı tekrar anahtarı ikinci siparişi açmaz (D6). */
export async function placeOrder(
  accessToken: string | undefined,
  idempotencyKey: string,
  body: Readonly<Record<string, unknown>>,
): Promise<{ readonly status: number; readonly placed: OrderPlaced }> {
  const result = await apiSend<OrderPlaced>("POST", ORDERS_PATH, { accessToken, body, idempotencyKey });
  return { status: result.status, placed: requireBody(result.data) };
}

/** Ödemeyi sağlayıcıdan doğrular; tekrar çağrılabilir (D8). */
export async function completePayment(accessToken: string, orderNumber: string): Promise<OrderDetail | null> {
  try {
    return requireBody(
      (await apiSend<OrderDetail>("POST", `${ORDERS_PATH}/${encodeURIComponent(orderNumber)}/payment`, { accessToken })).data,
    );
  } catch (error) {
    return nullIfNotFound(error);
  }
}

export async function listOwnOrders(accessToken: string, page: number, size: number): Promise<OrderPage> {
  return apiGet<OrderPage>(ORDERS_PATH, { accessToken, query: { page, size } });
}

/** Alıcının siparişi; yoksa veya başkasınınsa `null`. */
export async function getOwnOrder(accessToken: string, orderNumber: string): Promise<OrderDetail | null> {
  try {
    return await apiGet<OrderDetail>(`${ORDERS_PATH}/${encodeURIComponent(orderNumber)}`, { accessToken });
  } catch (error) {
    return nullIfNotFound(error);
  }
}

export async function listSellerOrders(
  accessToken: string,
  status: SellerOrderStatus | undefined,
  page: number,
  size: number,
): Promise<SellerOrderPage> {
  return apiGet<SellerOrderPage>(SELLER_ORDERS_PATH, { accessToken, query: { status, page, size } });
}

/** Satıcının satıcı siparişi; yoksa veya başkasınınsa `null`. */
export async function getSellerOrder(accessToken: string, id: number): Promise<SellerOrderDetail | null> {
  try {
    return await apiGet<SellerOrderDetail>(`${SELLER_ORDERS_PATH}/${id}`, { accessToken });
  } catch (error) {
    return nullIfNotFound(error);
  }
}

/** Alıcının kargodan önce iptali (D9); gerekçe isteğe bağlıdır. */
export async function cancelAsBuyer(
  accessToken: string | undefined,
  orderNumber: string,
  sellerOrderId: number,
  body: Readonly<Record<string, unknown>>,
): Promise<OrderDetail> {
  const path = `${ORDERS_PATH}/${encodeURIComponent(orderNumber)}/seller-orders/${sellerOrderId}/cancel`;
  return requireBody((await apiSend<OrderDetail>("POST", path, { accessToken, body })).data);
}

/** Satıcının gerekçeli iptali (D9). */
export async function cancelAsSeller(
  accessToken: string | undefined,
  id: number,
  body: Readonly<Record<string, unknown>>,
): Promise<SellerOrderDetail> {
  return requireBody((await apiSend<SellerOrderDetail>("POST", `${SELLER_ORDERS_PATH}/${id}/cancel`, { accessToken, body })).data);
}

function nullIfNotFound(error: unknown): null {
  if (error instanceof ApiRequestError && error.status === HTTP_NOT_FOUND) {
    return null;
  }
  throw error;
}

function requireBody<T>(data: T | undefined): T {
  if (data === undefined) {
    throw new Error("Sunucu beklenen gövdeyi döndürmedi.");
  }
  return data;
}

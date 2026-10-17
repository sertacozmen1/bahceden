import type { components } from "@contract";
import { ApiRequestError, apiDownload, apiGet, apiSend } from "@/lib/apiClient";

/**
 * Siparişler (sunucu tarafı). `api`'nin `/orders` ve `/sellers/me/orders`
 * uçlarını sarar (cart-and-checkout, orders ve shipments spec'leri,
 * add-cart-and-checkout D6, D8, add-shipments D3, D4). Tutarları ve durumları
 * sunucu belirler; ekran yalnızca gösterir.
 */
export type OrderPlaced = components["schemas"]["OrderPlaced"];
export type OrderDetail = components["schemas"]["OrderDetail"];
export type OrderSummary = components["schemas"]["OrderSummary"];
export type OrderStatus = OrderDetail["status"];
export type BuyerSellerOrderView = components["schemas"]["BuyerSellerOrderView"];
export type SellerOrderStatus = BuyerSellerOrderView["status"];
export type SellerOrderSummary = components["schemas"]["SellerOrderSummary"];
export type SellerOrderDetail = components["schemas"]["SellerOrderDetail"];
export type OrderItemView = components["schemas"]["OrderItemView"];
export type Shipment = components["schemas"]["Shipment"];
export type ShipmentStatus = Shipment["status"];
export type PackageSize = Shipment["packageSize"];
type OrderPage = components["schemas"]["PageResponseOrderSummary"];
type SellerOrderPage = components["schemas"]["PageResponseSellerOrderSummary"];

/** Satıcı listesindeki süzgeç seçenekleri; `api`'nin kabul ettiği durumlar, iş akışı sırasıyla. */
export const sellerOrderFilters = [
  "NEW",
  "SHIPPED",
  "DELIVERED",
  "DELIVERY_FAILED",
  "REFUNDED",
  "CLOSED",
  "CANCELLED",
] as const satisfies readonly SellerOrderStatus[];

/** Koli boyutları, küçükten büyüğe (add-shipments D3). */
export const packageSizes = ["SMALL", "MEDIUM", "LARGE"] as const satisfies readonly PackageSize[];

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

/** Kargoya hazırla: gönderi açar; açıksa mevcut gönderiyi döner (add-shipments D3). */
export async function createShipment(
  accessToken: string | undefined,
  id: number,
  body: Readonly<Record<string, unknown>>,
): Promise<{ readonly status: number; readonly shipment: Shipment }> {
  const result = await apiSend<Shipment>("POST", `${SELLER_ORDERS_PATH}/${id}/shipment`, { accessToken, body });
  return { status: result.status, shipment: requireBody(result.data) };
}

/** Etiketin ham yanıtı; Route Handler onu tarayıcıya akış olarak iletir (D4). */
export async function downloadShipmentLabel(accessToken: string | undefined, id: number): Promise<Response> {
  return apiDownload(`${SELLER_ORDERS_PATH}/${id}/shipment/label`, accessToken);
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

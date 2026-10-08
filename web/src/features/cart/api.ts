import type { components } from "@contract";
import { apiGet, apiSend } from "@/lib/apiClient";

/**
 * Sepet (sunucu tarafı). `api`'nin `/cart` uçlarını sarar (cart-and-checkout
 * spec'i, add-cart-and-checkout D4). Tutarları sunucu hesaplar; ekran
 * yalnızca gösterir (AGENTS.md §12.1).
 */
export type CartView = components["schemas"]["CartView"];
export type CartSellerGroup = components["schemas"]["CartSellerGroup"];
export type CartLine = components["schemas"]["CartLine"];

const CART_PATH = "/api/v1/cart";

export async function getCart(accessToken: string): Promise<CartView> {
  return apiGet<CartView>(CART_PATH, { accessToken });
}

/** Gövde BFF'ten gelir ve olduğu gibi iletilir; adet doğrulamasının otoritesi `api`'dir. */
export async function setCartItem(
  accessToken: string | undefined,
  productId: number,
  body: Readonly<Record<string, unknown>>,
): Promise<CartView> {
  return requireBody((await apiSend<CartView>("PUT", `${CART_PATH}/items/${productId}`, { accessToken, body })).data);
}

export async function removeCartItem(accessToken: string | undefined, productId: number): Promise<CartView> {
  return requireBody((await apiSend<CartView>("DELETE", `${CART_PATH}/items/${productId}`, { accessToken })).data);
}

function requireBody<T>(data: T | undefined): T {
  if (data === undefined) {
    throw new Error("Sunucu beklenen gövdeyi döndürmedi.");
  }
  return data;
}

import type { components } from "@contract";
import { apiGet, apiSend } from "@/lib/apiClient";

/**
 * Teslimat illeri ve hesabın adresleri (sunucu tarafı). `api`'nin
 * `/addresses` ve `/delivery/provinces` uçlarını sarar (cart-and-checkout
 * spec'i, add-cart-and-checkout D5). Adres kişisel veridir; yalnızca sahibine
 * gösterilir.
 */
export type AddressView = components["schemas"]["AddressView"];
export type DeliveryProvince = components["schemas"]["DeliveryProvince"];

const ADDRESSES_PATH = "/api/v1/addresses";

/** Teslimat illeri değişmeyen bir yapılandırmadır; kısa süre önbelleklenir. */
const PROVINCES_REVALIDATE_SECONDS = 300;

export async function listDeliveryProvinces(): Promise<readonly DeliveryProvince[]> {
  const page = await apiGet<components["schemas"]["PageResponseDeliveryProvince"]>("/api/v1/delivery/provinces", {
    revalidateSeconds: PROVINCES_REVALIDATE_SECONDS,
  });
  return page.items;
}

export async function listAddresses(accessToken: string): Promise<readonly AddressView[]> {
  const page = await apiGet<components["schemas"]["PageResponseAddressView"]>(ADDRESSES_PATH, { accessToken });
  return page.items;
}

/** Gövde BFF'ten gelir ve olduğu gibi iletilir; doğrulama otoritesi `api`'dir. */
export async function createAddress(
  accessToken: string | undefined,
  body: Readonly<Record<string, unknown>>,
): Promise<{ readonly status: number; readonly address: AddressView }> {
  const result = await apiSend<AddressView>("POST", ADDRESSES_PATH, { accessToken, body });
  return { status: result.status, address: requireBody(result.data) };
}

export async function updateAddress(
  accessToken: string | undefined,
  id: number,
  body: Readonly<Record<string, unknown>>,
): Promise<AddressView> {
  return requireBody((await apiSend<AddressView>("PUT", `${ADDRESSES_PATH}/${id}`, { accessToken, body })).data);
}

export async function deleteAddress(accessToken: string | undefined, id: number): Promise<void> {
  await apiSend<undefined>("DELETE", `${ADDRESSES_PATH}/${id}`, { accessToken });
}

function requireBody<T>(data: T | undefined): T {
  if (data === undefined) {
    throw new Error("Sunucu beklenen gövdeyi döndürmedi.");
  }
  return data;
}

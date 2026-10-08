import type { components } from "@contract";
import { ApiRequestError, apiGet, apiSend } from "@/lib/apiClient";

/**
 * Satıcının ödeme hesabı (sunucu tarafı). `api`'nin
 * `/sellers/me/payout-account` uçlarını sarar (seller-payout-details spec'i,
 * add-cart-and-checkout D2). IBAN ve kimlik numarası yanıtta maskelidir.
 */
export type PayoutAccountView = components["schemas"]["PayoutAccountView"];
export type PayoutLegalType = PayoutAccountView["legalType"];

/** Formdaki satıcı türleri; sözleşmedeki enum ile aynı. */
export const payoutLegalTypes = ["INDIVIDUAL", "SOLE_PROPRIETOR", "COMPANY"] as const satisfies readonly PayoutLegalType[];

/** Vergi dairesi isteyen türler; kural `api`'dedir, form yalnızca alanı gösterip gizler. */
export const legalTypesWithTaxOffice: readonly PayoutLegalType[] = ["SOLE_PROPRIETOR", "COMPANY"];

const HTTP_NOT_FOUND = 404;
const PAYOUT_ACCOUNT_PATH = "/api/v1/sellers/me/payout-account";

/** Ödeme hesabı; henüz girilmediyse `null` (api 404). */
export async function getOwnPayoutAccount(accessToken: string): Promise<PayoutAccountView | null> {
  try {
    return await apiGet<PayoutAccountView>(PAYOUT_ACCOUNT_PATH, { accessToken });
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === HTTP_NOT_FOUND) {
      return null;
    }
    throw error;
  }
}

/** Satıcının ürünleri satın alınabilir mi: etkin ödeme hesabı (D1). */
export function acceptsPayments(account: PayoutAccountView | null): boolean {
  return account?.status === "ACTIVE";
}

/** Gövde BFF'ten gelir ve olduğu gibi iletilir; doğrulama otoritesi `api`'dir. */
export async function savePayoutAccount(
  accessToken: string | undefined,
  body: Readonly<Record<string, unknown>>,
): Promise<PayoutAccountView> {
  const result = await apiSend<PayoutAccountView>("PUT", PAYOUT_ACCOUNT_PATH, { accessToken, body });
  if (result.data === undefined) {
    throw new Error("Sunucu beklenen gövdeyi döndürmedi.");
  }
  return result.data;
}

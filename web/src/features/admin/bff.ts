import type { SellerDecision } from "./api";

/**
 * Admin ekranlarının BFF yolları (ADR-023). `api` sözleşmesi değildir; token
 * hiçbir yanıtta yer almaz.
 */
export const adminBffPaths = {
  decision: (sellerId: number, decision: SellerDecision) => `/api/admin/sellers/${sellerId}/${decision}`,
  document: (sellerId: number) => `/api/admin/sellers/${sellerId}/document`,
} as const;

import type { BuyerClaimAction, ClaimPhotoSize } from "./api";

/**
 * Taleplerin BFF ve medya yolları (ADR-023, ADR-084). `api` sözleşmesi
 * değildir; tarayıcı `api`'ye konuşmaz.
 */
export const claimBffPaths = {
  open: (orderNumber: string, sellerOrderId: number) =>
    `/api/orders/${encodeURIComponent(orderNumber)}/seller-orders/${sellerOrderId}/claim`,
  photo: (claimId: number, position: number, size: ClaimPhotoSize) =>
    `/media/claim-photos/${claimId}/${position}?size=${size}`,
  buyerAction: (orderNumber: string, sellerOrderId: number, action: BuyerClaimAction) =>
    `/api/orders/${encodeURIComponent(orderNumber)}/seller-orders/${sellerOrderId}/claim/${action}`,
  sellerResponse: (sellerOrderId: number) => `/api/seller/orders/${sellerOrderId}/claim/response`,
  sellerPreview: (sellerOrderId: number) => `/api/seller/orders/${sellerOrderId}/claim/preview`,
  adminPreview: (claimId: number) => `/api/admin/claims/${claimId}/preview`,
  adminDecision: (claimId: number) => `/api/admin/claims/${claimId}/decision`,
  form: (orderNumber: string, sellerOrderId: number): `/account/orders/${string}/claim?sellerOrder=${number}` =>
    `/account/orders/${encodeURIComponent(orderNumber)}/claim?sellerOrder=${sellerOrderId}`,
} as const;

/** Talep formunun satır alanları; BFF yalnızca bu biçimdeki alanları iletir. */
export const claimLineField = /^lines\[(\d{1,2})\]\.(orderItemId|quantity)$/;

/**
 * Siparişlerin BFF yolları (ADR-023). `api` sözleşmesi değildir; token hiçbir
 * yanıtta yer almaz.
 */
export const orderBffPaths = {
  orders: "/api/orders",
  buyerCancel: (orderNumber: string, sellerOrderId: number) =>
    `/api/orders/${encodeURIComponent(orderNumber)}/seller-orders/${sellerOrderId}/cancel`,
  sellerCancel: (id: number) => `/api/seller/orders/${id}/cancel`,
} as const;

/** Tekrar anahtarının başlık adı; `api` ile aynı (AGENTS.md §11.5). */
export const idempotencyHeader = "Idempotency-Key";

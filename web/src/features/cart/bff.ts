/**
 * Sepetin BFF yolları (ADR-023). `api` sözleşmesi değildir; token hiçbir
 * yanıtta yer almaz.
 */
export const cartBffPaths = {
  item: (productId: number) => `/api/cart/items/${productId}`,
} as const;

/** Sepette adet sınırı (1–99); erken geri bildirim içindir, otorite `api`'dir. */
export const maxCartQuantity = 99;

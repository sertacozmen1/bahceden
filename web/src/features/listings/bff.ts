/**
 * Satıcı ilanlarının BFF yolları (ADR-023). `api` sözleşmesi değildir; token
 * hiçbir yanıtta yer almaz.
 */
export const listingBffPaths = {
  listings: "/api/seller/products",
  listing: (id: number) => `/api/seller/products/${id}`,
  photos: (id: number) => `/api/seller/products/${id}/photos`,
  photo: (id: number, photoId: number) => `/api/seller/products/${id}/photos/${photoId}`,
  photoOrder: (id: number) => `/api/seller/products/${id}/photos/order`,
  action: (id: number, action: string) => `/api/seller/products/${id}/${action}`,
} as const;

/**
 * Tarayıcıdan gelen gövdeyi `api` isteğine çevirir: yalnızca bilinen alanlar,
 * tipleri daraltılarak. Eksik ya da yanlış tipli alan `api`'nin doğrulamasına
 * bırakılır (otorite `api`); burada uydurma değer üretilmez.
 */
export function listingBodyFrom(body: Record<string, unknown>): Record<string, unknown> {
  const pick = (name: string) => body[name];
  return {
    name: pick("name"),
    description: pick("description"),
    unit: pick("unit"),
    priceAmount: pick("priceAmount"),
    stockQuantity: pick("stockQuantity"),
    dispatchDays: pick("dispatchDays"),
  };
}

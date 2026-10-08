/**
 * Adreslerin BFF yolları (ADR-023). `api` sözleşmesi değildir; token hiçbir
 * yanıtta yer almaz.
 */
export const addressBffPaths = {
  addresses: "/api/addresses",
  address: (id: number) => `/api/addresses/${id}`,
} as const;

/** Tarayıcıdan gelen gövdeden yalnızca bilinen alanlar; doğrulama otoritesi `api`'dir. */
export function addressBodyFrom(body: Record<string, unknown>): Record<string, unknown> {
  return {
    title: body.title,
    recipientName: body.recipientName,
    phone: body.phone,
    provinceCode: body.provinceCode,
    district: body.district,
    addressLine: body.addressLine,
    postalCode: body.postalCode,
  };
}

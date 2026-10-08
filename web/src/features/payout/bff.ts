/**
 * Satıcı ödeme hesabının BFF yolu (ADR-023). `api` sözleşmesi değildir; token
 * hiçbir yanıtta yer almaz.
 */
export const payoutBffPath = "/api/seller/payout-account";

/**
 * Tarayıcıdan gelen gövdeden yalnızca bilinen alanlar. Eksik ya da yanlış tipli
 * alan `api`'nin doğrulamasına bırakılır (otorite `api`).
 */
export function payoutBodyFrom(body: Record<string, unknown>): Record<string, unknown> {
  return {
    legalType: body.legalType,
    legalName: body.legalName,
    taxId: body.taxId,
    taxOffice: body.taxOffice,
    iban: body.iban,
  };
}

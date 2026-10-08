/**
 * Satıcı başvurusunun BFF yolları: tarayıcıdaki formun ve belge bağlantısının
 * konuştuğu kendi Route Handler'larımız (ADR-023). `api` sözleşmesi değildir;
 * token hiçbir yanıtta yer almaz.
 */
export const sellerBffPaths = {
  application: "/api/seller/application",
  document: "/api/seller/application/document",
} as const;

/** Formun gönderdiği alanlar; Route Handler yalnızca bunları `api`'ye iletir. */
export const sellerApplicationFields = ["displayName", "city", "phone", "documentType", "document"] as const;

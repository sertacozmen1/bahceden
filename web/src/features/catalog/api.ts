import type { components } from "@contract";
import { ApiRequestError, apiGet } from "@/lib/apiClient";

/**
 * Katalog verisi çekme.
 *
 * Tipler sözleşmeden üretilir; elle yazılmaz (ADR-003). Sözleşmede olmayan bir
 * alan burada varsayılamaz: ihtiyaç doğarsa önce `api` DTO'su değişir, sözleşme
 * yeniden üretilir, sonra bu katman kullanır (AGENTS.md K2).
 */

export type ProductSummary = components["schemas"]["ProductSummary"];
export type ProductPage = components["schemas"]["PageResponseProductSummary"];
export type ProductDetail = components["schemas"]["ProductDetail"];

const HTTP_NOT_FOUND = 404;

/** Vitrinde yayınlanmış ürünlerin sayfası. */
export async function fetchPublishedProducts(page: number, size: number): Promise<ProductPage> {
  return apiGet<ProductPage>("/api/v1/products", {
    query: { page, size },
    // Vitrin verisi kısa süre önbelleklenir: liste her istekte veritabanına
    // gitmek zorunda değil, ama fiyat değişikliği de dakikalarca beklemez.
    revalidateSeconds: 30,
  });
}

/**
 * Ürün detayı (storefront-catalog "Ürün detayı"). Vitrinde görünmeyen ürün
 * `api`'de 404'tür; burada `null` olur ve sayfa "bulunamadı" gösterir.
 * Liste gibi kısa süre önbelleklenir.
 */
export async function fetchProduct(slug: string): Promise<ProductDetail | null> {
  try {
    return await apiGet<ProductDetail>(`/api/v1/products/${encodeURIComponent(slug)}`, { revalidateSeconds: 30 });
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === HTTP_NOT_FOUND) {
      return null;
    }
    throw error;
  }
}

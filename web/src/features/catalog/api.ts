import type { components } from "@contract";
import { apiGet } from "@/lib/apiClient";

/**
 * Katalog verisi çekme.
 *
 * Tipler sözleşmeden üretilir; elle yazılmaz (ADR-003). Sözleşmede olmayan bir
 * alan burada varsayılamaz: ihtiyaç doğarsa önce `api` DTO'su değişir, sözleşme
 * yeniden üretilir, sonra bu katman kullanır (AGENTS.md K2).
 */

export type ProductSummary = components["schemas"]["ProductSummary"];
export type ProductPage = components["schemas"]["PageResponse"];

/** Vitrinde yayınlanmış ürünlerin sayfası. */
export async function fetchPublishedProducts(page: number, size: number): Promise<ProductPage> {
  return apiGet<ProductPage>("/api/v1/products", {
    query: { page, size },
    // Vitrin verisi kısa süre önbelleklenir: liste her istekte veritabanına
    // gitmek zorunda değil, ama fiyat değişikliği de dakikalarca beklemez.
    revalidateSeconds: 30,
  });
}

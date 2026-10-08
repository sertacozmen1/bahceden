import { serveDocument } from "@/features/seller/documentResponse";
import { apiDownload } from "@/lib/apiClient";
import { photoUrl } from "@/lib/productPhotos";

/**
 * İlan fotoğrafının tarayıcıya aktarımı (add-product-listings D5). Tarayıcı
 * `api`'ye konuşmaz; fotoğraf buradan akış olarak, `api`'nin uzun önbellek ve
 * `nosniff` başlıklarıyla gelir. Akış ve başlık iletimi belge aktarımıyla
 * aynı yardımcıdadır (K9); uç herkese açık olduğu için oturum kullanılmaz.
 */
export async function GET(_request: Request, context: RouteContext<"/media/product-photos/[key]">): Promise<Response> {
  const { key } = await context.params;
  return serveDocument(() => apiDownload(`/api/v1/product-photos/${encodeURIComponent(key)}`, undefined), photoUrl(key));
}

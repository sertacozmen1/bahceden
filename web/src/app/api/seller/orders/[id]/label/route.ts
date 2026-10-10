import { downloadShipmentLabel } from "@/features/orders/api";
import { orderBffPaths } from "@/features/orders/bff";
import { serveDocument } from "@/features/seller/documentResponse";
import { readAccessToken } from "@/lib/session";

/**
 * Gönderi etiketi (BFF, add-shipments D4). Etiket alıcının adını ve adresini
 * taşır; yeni sekmede açılır ve oradan yazdırılır. Koruma başlıkları ve akış
 * satıcı belgesiyle aynı yoldan geçer (K9).
 */
export async function GET(_request: Request, context: RouteContext<"/api/seller/orders/[id]/label">): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  const accessToken = await readAccessToken();
  return serveDocument(() => downloadShipmentLabel(accessToken, id), orderBffPaths.label(id));
}

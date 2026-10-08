import { downloadSellerDocumentForAdmin } from "@/features/admin/api";
import { adminBffPaths } from "@/features/admin/bff";
import { serveDocument } from "@/features/seller/documentResponse";
import { readAccessToken } from "@/lib/session";

/**
 * Admin için satıcının güncel belgesi (BFF, add-seller-onboarding D6, D7).
 * Rol denetimi `api`'dedir: admin olmayan istek 403'ü ortak gövdeyle alır.
 * Sayı olmayan kimlik `api`'ye hiç gitmez.
 */
export async function GET(_request: Request, context: RouteContext<"/api/admin/sellers/[id]/document">): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  const accessToken = await readAccessToken();
  return serveDocument(() => downloadSellerDocumentForAdmin(accessToken, id), adminBffPaths.document(id));
}

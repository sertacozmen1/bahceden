import { handleBffMutation } from "@/features/account/routeSupport";
import { respondToClaim } from "@/features/claims/api";
import { readAccessToken } from "@/lib/session";

/**
 * Satıcının ayıplı ürün talebine yanıtı (BFF, ADR-080). Köken denetimi ve hata
 * iletimi ortak adımdadır (ADR-023); yalnızca karar, satırlar ve gerekçe
 * iletilir. Geçersiz kimlik `api`'ye gitmez.
 */
export async function POST(request: Request, context: RouteContext<"/api/seller/orders/[id]/claim/response">): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json(
      await respondToClaim(await readAccessToken(), id, { decision: body.decision, lines: body.lines, reason: body.reason }),
    ),
  );
}

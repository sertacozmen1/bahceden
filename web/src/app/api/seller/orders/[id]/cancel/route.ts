import { handleBffMutation } from "@/features/account/routeSupport";
import { cancelAsSeller } from "@/features/orders/api";
import { readAccessToken } from "@/lib/session";

/**
 * Satıcının gerekçeli iptali (BFF, add-cart-and-checkout D9). Köken denetimi
 * ve hata iletimi ortak adımdadır (ADR-023); gövdeden yalnızca gerekçe
 * iletilir. Geçersiz kimlik `api`'ye gitmez.
 */
export async function POST(request: Request, context: RouteContext<"/api/seller/orders/[id]/cancel">): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json(await cancelAsSeller(await readAccessToken(), id, { reason: body.reason })),
  );
}

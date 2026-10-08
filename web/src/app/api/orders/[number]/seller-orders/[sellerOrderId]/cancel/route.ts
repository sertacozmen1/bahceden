import { handleBffMutation } from "@/features/account/routeSupport";
import { cancelAsBuyer } from "@/features/orders/api";
import { readAccessToken } from "@/lib/session";

/**
 * Alıcının kargodan önce iptali (BFF, add-cart-and-checkout D9). Köken
 * denetimi ve hata iletimi ortak adımdadır (ADR-023); gövdeden yalnızca
 * gerekçe iletilir. Geçersiz kimlik `api`'ye gitmez.
 */
export async function POST(
  request: Request,
  context: RouteContext<"/api/orders/[number]/seller-orders/[sellerOrderId]/cancel">,
): Promise<Response> {
  const { number, sellerOrderId: rawId } = await context.params;
  const sellerOrderId = Number(rawId);
  if (!Number.isSafeInteger(sellerOrderId) || sellerOrderId <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json(await cancelAsBuyer(await readAccessToken(), number, sellerOrderId, { reason: body.reason })),
  );
}

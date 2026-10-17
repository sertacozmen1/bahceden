import { handleBffMutation } from "@/features/account/routeSupport";
import { buyerClaimAction, buyerClaimActions } from "@/features/claims/api";
import { readAccessToken } from "@/lib/session";

/**
 * Alıcının talep eylemi (BFF, ADR-080): kabul ya da yönetime taşıma. Eylem
 * beyaz listeden; geçersiz eylem veya kimlik `api`'ye gitmez. Gövde iletilmez.
 */
export async function POST(
  request: Request,
  context: RouteContext<"/api/orders/[number]/seller-orders/[sellerOrderId]/claim/[action]">,
): Promise<Response> {
  const { number, sellerOrderId, action: rawAction } = await context.params;
  const id = Number(sellerOrderId);
  const action = buyerClaimActions.find((candidate) => candidate === rawAction);
  if (!Number.isSafeInteger(id) || id <= 0 || action === undefined) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async () =>
    Response.json(await buyerClaimAction(await readAccessToken(), decodeURIComponent(number), id, action)),
  );
}

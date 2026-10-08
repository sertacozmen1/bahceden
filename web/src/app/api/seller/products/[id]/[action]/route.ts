import { listingActions, transitionListing } from "@/features/listings/api";
import { handleBffMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * Satıcının yayın geçişi (BFF, D1): yayına al, yayından çek, arşivle, geri al.
 * Eylem beyaz listede değilse `api`'ye hiç gidilmez.
 */
export async function POST(
  request: Request,
  context: RouteContext<"/api/seller/products/[id]/[action]">,
): Promise<Response> {
  const { id: rawId, action: rawAction } = await context.params;
  const id = Number(rawId);
  const action = listingActions.find((candidate) => candidate === rawAction);
  if (action === undefined || !Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async () =>
    Response.json(await transitionListing(await readAccessToken(), id, action)),
  );
}

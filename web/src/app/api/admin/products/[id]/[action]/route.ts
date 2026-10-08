import { decideProduct, productDecisions } from "@/features/admin/api";
import { handleBffMutation, stringField } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * Admin ilan denetimi (BFF, add-product-listings D7): kaldır, yeniden aç.
 * Eylem beyaz listede değilse `api`'ye hiç gidilmez; köken denetimi ortak adımda.
 */
export async function POST(
  request: Request,
  context: RouteContext<"/api/admin/products/[id]/[action]">,
): Promise<Response> {
  const { id: rawId, action } = await context.params;
  const id = Number(rawId);
  const decision = productDecisions.find((candidate) => candidate === action);
  if (decision === undefined || !Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json((await decideProduct(await readAccessToken(), id, decision, stringField(body, "reason"))) ?? null),
  );
}

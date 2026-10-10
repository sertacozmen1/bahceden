import { resolveUndelivered, undeliveredDecisions } from "@/features/admin/api";
import { handleBffMutation, stringField } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * Teslim edilemeyen sipariş için admin kararı (BFF, add-shipments D7): iade
 * veya gerekçeyle kapatma. Karar beyaz listede değilse `api`'ye gidilmez;
 * köken denetimi ve hata iletimi ortak adımda (ADR-023).
 */
export async function POST(
  request: Request,
  context: RouteContext<"/api/admin/seller-orders/[id]/resolution">,
): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) => {
    const decision = undeliveredDecisions.find((candidate) => candidate === body.decision);
    if (decision === undefined) {
      return new Response(null, { status: 400 });
    }
    return Response.json(
      (await resolveUndelivered(await readAccessToken(), id, decision, stringField(body, "reason").trim())) ?? null,
    );
  });
}

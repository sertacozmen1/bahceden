import { decideSeller, sellerDecisions } from "@/features/admin/api";
import { handleBffMutation, stringField } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * Admin kararı (BFF, add-seller-onboarding D7): onay, ret, askı, askıdan
 * çıkarma. Eylem beyaz listeyle doğrulanır; listede olmayan bir yol `api`'ye
 * hiç gitmez. Köken denetimi ve hata iletimi ortak adımdadır (ADR-023).
 */
export async function POST(
  request: Request,
  context: RouteContext<"/api/admin/sellers/[id]/[action]">,
): Promise<Response> {
  const { id: rawId, action } = await context.params;
  const id = Number(rawId);
  const decision = sellerDecisions.find((candidate) => candidate === action);
  if (decision === undefined || !Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) => {
    const updated = await decideSeller(await readAccessToken(), id, decision, stringField(body, "reason"));
    return Response.json(updated ?? null, { status: 200 });
  });
}

import { handleBffMutation } from "@/features/account/routeSupport";
import { previewClaimForAdmin } from "@/features/claims/api";
import { readAccessToken } from "@/lib/session";

/**
 * Admin kararının para etkisi (BFF, ADR-086). Yan etkisizdir ama gövde
 * taşıdığı için POST'tur; köken denetimi yine uygulanır. Yalnızca satırlar iletilir.
 */
export async function POST(request: Request, context: RouteContext<"/api/admin/claims/[id]/preview">): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json(await previewClaimForAdmin(await readAccessToken(), id, { lines: body.lines })),
  );
}

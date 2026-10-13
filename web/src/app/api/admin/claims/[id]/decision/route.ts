import { handleBffMutation } from "@/features/account/routeSupport";
import { decideClaim } from "@/features/claims/api";
import { readAccessToken } from "@/lib/session";

/**
 * Admin'in talep kararı (BFF, ADR-080). Köken denetimi ve hata iletimi ortak
 * adımdadır (ADR-023); yalnızca karar, satırlar ve gerekçe iletilir.
 */
export async function POST(request: Request, context: RouteContext<"/api/admin/claims/[id]/decision">): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json(await decideClaim(await readAccessToken(), id, { decision: body.decision, lines: body.lines, reason: body.reason })),
  );
}

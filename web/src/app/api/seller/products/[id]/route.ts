import { updateListing } from "@/features/listings/api";
import { listingBodyFrom } from "@/features/listings/bff";
import { handleBffMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * İlan bilgilerini güncelleme (BFF). Sayı olmayan kimlik `api`'ye hiç gitmez;
 * başka satıcının ilanı `api`'den 404 olarak gelir ve aynen iletilir.
 */
export async function PUT(request: Request, context: RouteContext<"/api/seller/products/[id]">): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json(await updateListing(await readAccessToken(), id, listingBodyFrom(body))),
  );
}

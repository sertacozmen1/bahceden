import { reorderListingPhotos } from "@/features/listings/api";
import { handleBffMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/** Fotoğraf sırası (BFF, D3). Yalnızca `photoIds` iletilir; geçerliliği `api` denetler. */
export async function PUT(
  request: Request,
  context: RouteContext<"/api/seller/products/[id]/photos/order">,
): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json(await reorderListingPhotos(await readAccessToken(), id, { photoIds: body.photoIds })),
  );
}

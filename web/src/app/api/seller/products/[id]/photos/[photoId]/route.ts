import { deleteListingPhoto } from "@/features/listings/api";
import { handleBffMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/** İlan fotoğrafını silme (BFF). Sayı olmayan kimlikler `api`'ye gitmez. */
export async function DELETE(
  request: Request,
  context: RouteContext<"/api/seller/products/[id]/photos/[photoId]">,
): Promise<Response> {
  const params = await context.params;
  const id = Number(params.id);
  const photoId = Number(params.photoId);
  if (!Number.isSafeInteger(id) || id <= 0 || !Number.isSafeInteger(photoId) || photoId <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async () =>
    Response.json(await deleteListingPhoto(await readAccessToken(), id, photoId)),
  );
}

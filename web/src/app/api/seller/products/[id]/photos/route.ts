import { uploadListingPhoto } from "@/features/listings/api";
import { handleBffFormMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * İlana tek fotoğraf yükleme (BFF, add-product-listings D3). Yalnızca `photo`
 * alanı iletilir; tür, boyut ve piksel sınırı `api`'de içerikten denetlenir.
 */
export async function POST(request: Request, context: RouteContext<"/api/seller/products/[id]/photos">): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffFormMutation(request, async (form) => {
    const outgoing = new FormData();
    const photo = form.get("photo");
    if (photo !== null) {
      outgoing.set("photo", photo);
    }
    return Response.json(await uploadListingPhoto(await readAccessToken(), id, outgoing));
  });
}

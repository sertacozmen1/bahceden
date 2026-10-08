import { createListing } from "@/features/listings/api";
import { listingBodyFrom } from "@/features/listings/bff";
import { handleBffMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * Taslak ilan oluşturma (BFF, add-product-listings D2). Köken denetimi ve
 * hata iletimi ortak adımdadır (ADR-023); yalnızca bilinen alanlar iletilir.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    const { status, listing } = await createListing(await readAccessToken(), listingBodyFrom(body));
    return Response.json(listing, { status });
  });
}

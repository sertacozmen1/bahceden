import { handleBffMutation } from "@/features/account/routeSupport";
import { createAddress } from "@/features/addresses/api";
import { addressBodyFrom } from "@/features/addresses/bff";
import { readAccessToken } from "@/lib/session";

/**
 * Adres ekleme (BFF, add-cart-and-checkout D5). Köken denetimi ve hata
 * iletimi ortak adımdadır (ADR-023); yalnızca adres alanları iletilir.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    const { status, address } = await createAddress(await readAccessToken(), addressBodyFrom(body));
    return Response.json(address, { status });
  });
}

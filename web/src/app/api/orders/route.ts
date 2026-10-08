import { handleBffMutation } from "@/features/account/routeSupport";
import { placeOrder } from "@/features/orders/api";
import { idempotencyHeader } from "@/features/orders/bff";
import { readAccessToken } from "@/lib/session";

/**
 * Sepetten sipariş (BFF, add-cart-and-checkout D6). Köken denetimi ve hata
 * iletimi ortak adımdadır (ADR-023). Gövdeden yalnızca adres kimliği,
 * başlıklardan yalnızca tekrar anahtarı iletilir; anahtarın doğrulaması
 * `api`'dedir.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    const { status, placed } = await placeOrder(
      await readAccessToken(),
      request.headers.get(idempotencyHeader) ?? "",
      { addressId: body.addressId },
    );
    return Response.json(placed, { status });
  });
}

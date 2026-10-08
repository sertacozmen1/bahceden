import { removeCartItem, setCartItem } from "@/features/cart/api";
import { handleBffMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * Sepet satırı (BFF, add-cart-and-checkout D4): adet koyma ve silme. Köken
 * denetimi ve hata iletimi ortak adımdadır (ADR-023); gövdeden yalnızca adet
 * iletilir. Geçersiz ürün kimliği `api`'ye gitmez.
 */
export async function PUT(
  request: Request,
  context: RouteContext<"/api/cart/items/[productId]">,
): Promise<Response> {
  const productId = parseId((await context.params).productId);
  if (productId === undefined) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json(await setCartItem(await readAccessToken(), productId, { quantity: body.quantity })),
  );
}

export async function DELETE(
  request: Request,
  context: RouteContext<"/api/cart/items/[productId]">,
): Promise<Response> {
  const productId = parseId((await context.params).productId);
  if (productId === undefined) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async () => Response.json(await removeCartItem(await readAccessToken(), productId)));
}

function parseId(raw: string): number | undefined {
  const id = Number(raw);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

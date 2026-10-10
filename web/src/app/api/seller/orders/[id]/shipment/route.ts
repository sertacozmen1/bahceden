import { handleBffMutation } from "@/features/account/routeSupport";
import { createShipment } from "@/features/orders/api";
import { readAccessToken } from "@/lib/session";

/**
 * Kargoya hazırla (BFF, add-shipments D3). Köken denetimi ve hata iletimi
 * ortak adımdadır (ADR-023); gövdeden yalnızca koli boyutu iletilir. `api`'nin
 * durumu (201 açıldı, 200 zaten açıktı) aynen geçer. Geçersiz kimlik `api`'ye gitmez.
 */
export async function POST(request: Request, context: RouteContext<"/api/seller/orders/[id]/shipment">): Promise<Response> {
  const id = Number((await context.params).id);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) => {
    const { status, shipment } = await createShipment(await readAccessToken(), id, { packageSize: body.packageSize });
    return Response.json(shipment, { status });
  });
}

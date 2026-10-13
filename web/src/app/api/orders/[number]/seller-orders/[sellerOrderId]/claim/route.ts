import { handleBffFormMutation } from "@/features/account/routeSupport";
import { openClaim } from "@/features/claims/api";
import { claimLineField } from "@/features/claims/bff";
import { readAccessToken } from "@/lib/session";

/**
 * Alıcının ayıplı ürün talebi (BFF, ADR-081). Köken denetimi ve hata iletimi
 * ortak adımdadır (ADR-023). Yalnızca bilinen alanlar iletilir: açıklama,
 * `lines[i].orderItemId` / `lines[i].quantity` ve fotoğraflar. Satırın
 * siparişe aitliği ve fotoğrafların türü `api`'de denetlenir.
 */
export async function POST(
  request: Request,
  context: RouteContext<"/api/orders/[number]/seller-orders/[sellerOrderId]/claim">,
): Promise<Response> {
  const { number, sellerOrderId } = await context.params;
  const id = Number(sellerOrderId);
  if (!Number.isSafeInteger(id) || id <= 0) {
    return new Response(null, { status: 404 });
  }
  return handleBffFormMutation(request, async (form) => {
    const outgoing = new FormData();
    const description = form.get("description");
    if (typeof description === "string") {
      outgoing.set("description", description);
    }
    for (const [name, value] of form.entries()) {
      if (typeof value === "string" && claimLineField.test(name)) {
        outgoing.set(name, value);
      }
    }
    for (const photo of form.getAll("photos")) {
      // Form değeri ya metindir ya dosya; metin olarak gelen "fotoğraf" iletilmez.
      if (typeof photo !== "string") {
        outgoing.append("photos", photo);
      }
    }
    return Response.json(await openClaim(await readAccessToken(), decodeURIComponent(number), id, outgoing), {
      status: 201,
    });
  });
}

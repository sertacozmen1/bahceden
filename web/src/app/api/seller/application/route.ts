import { submitSellerApplication } from "@/features/seller/api";
import { sellerApplicationFields } from "@/features/seller/bff";
import { handleBffFormMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * Satıcı başvurusu (BFF, add-seller-onboarding D3). `POST` yeni başvuru, `PUT`
 * reddedilen başvurunun yeniden gönderimi. Kimlik çerezdeki erişim
 * token'ından gelir; token yoksa veya süresi geçmişse `api` 401 döner ve aynen
 * iletilir.
 *
 * Yalnızca bilinen alanlar iletilir: formun dışında eklenmiş bir alan `api`'ye
 * taşınmaz.
 */
export async function POST(request: Request): Promise<Response> {
  return forward("POST", request);
}

export async function PUT(request: Request): Promise<Response> {
  return forward("PUT", request);
}

async function forward(method: "POST" | "PUT", request: Request): Promise<Response> {
  return handleBffFormMutation(request, async (form) => {
    const outgoing = new FormData();
    for (const field of sellerApplicationFields) {
      const value = form.get(field);
      if (value !== null) {
        outgoing.set(field, value);
      }
    }
    const { status, seller } = await submitSellerApplication(method, outgoing, await readAccessToken());
    return Response.json(seller, { status });
  });
}

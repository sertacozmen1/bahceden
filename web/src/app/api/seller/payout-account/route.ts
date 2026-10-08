import { handleBffMutation } from "@/features/account/routeSupport";
import { savePayoutAccount } from "@/features/payout/api";
import { payoutBodyFrom } from "@/features/payout/bff";
import { readAccessToken } from "@/lib/session";

/**
 * Satıcının ödeme bilgilerini kaydeder (BFF, add-cart-and-checkout D2). Köken
 * denetimi ve hata iletimi ortak adımdadır (ADR-023); yalnızca bilinen alanlar
 * iletilir. Yanıtta numaralar maskelidir.
 */
export async function PUT(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) =>
    Response.json(await savePayoutAccount(await readAccessToken(), payoutBodyFrom(body))),
  );
}

import { requestVerificationLink } from "@/features/account/api";
import type { BffMessageResponse } from "@/features/account/bff";
import { handleBffMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";
import { texts } from "@/lib/texts";

/**
 * Yeni doğrulama bağlantısı (BFF, design.md D9). Adres istekten değil oturumdan
 * gelir; erişim token'ı yoksa `api` 401 döner ve aynen iletilir.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async () => {
    await requestVerificationLink(await readAccessToken());
    const response: BffMessageResponse = { message: texts.account.myAccount.resent };
    return Response.json(response, { status: 202 });
  });
}

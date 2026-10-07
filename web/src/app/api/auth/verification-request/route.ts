import { getTranslations } from "next-intl/server";
import { requestVerificationLink } from "@/features/account/api";
import type { BffMessageResponse } from "@/features/account/bff";
import { handleBffMutation } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * Yeni doğrulama bağlantısı (BFF, design.md D9). Adres istekten değil oturumdan
 * gelir; erişim token'ı yoksa `api` 401 döner ve aynen iletilir.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async () => {
    const t = await getTranslations();
    await requestVerificationLink(await readAccessToken());
    const response: BffMessageResponse = { message: t("account.myAccount.resent") };
    return Response.json(response, { status: 202 });
  });
}

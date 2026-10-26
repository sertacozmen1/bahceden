import { changeTwoFactor } from "@/features/account/api";
import { handleBffMutation, invalidRequestResponse, stringField } from "@/features/account/routeSupport";
import { readAccessToken } from "@/lib/session";

/**
 * İki adımlı girişi açma ve kapatma (BFF, add-two-factor-toggle D2).
 *
 * Oturumlar değişiklikle kapanmaz (ADR-103); çerezlere dokunulmaz. Kurallar
 * (mevcut parola, doğrulanmış e-posta, hız sınırı) `api`'dedir; hata gövdesi
 * aynen iletilir. Açık mı kapalı mı olacağı mantıksal değer değilse istek
 * `api`'ye gitmez.
 */
export async function PUT(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    const enabled = body.enabled;
    if (typeof enabled !== "boolean") {
      return invalidRequestResponse();
    }
    await changeTwoFactor(await readAccessToken(), enabled, stringField(body, "currentPassword"));
    return new Response(null, { status: 204 });
  });
}

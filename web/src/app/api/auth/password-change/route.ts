import { changePassword } from "@/features/account/api";
import { handleBffMutation, stringField } from "@/features/account/routeSupport";
import { clearSession, readAccessToken } from "@/lib/session";

/**
 * Oturum açıkken parola değiştirme (BFF, add-password-change D4).
 *
 * Başarıda `api` bu oturum dahil bütün oturumları kapatmıştır; çerezler hemen
 * silinir ki tarayıcıda iptal edilmiş bir yenileme token'ı kalmasın. Hata
 * durumunda (yanlış mevcut parola, hız sınırı) oturum geçerlidir, çerezlere
 * dokunulmaz ve `api`'nin hata gövdesi aynen iletilir.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    await changePassword(
      await readAccessToken(),
      stringField(body, "currentPassword"),
      stringField(body, "newPassword"),
    );
    await clearSession();
    return new Response(null, { status: 204 });
  });
}

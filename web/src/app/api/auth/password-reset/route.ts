import { resetPassword } from "@/features/account/api";
import { forwardedFor, handleBffMutation, stringField } from "@/features/account/routeSupport";

/**
 * Sıfırlama bağlantısıyla yeni parola (BFF). Oturum açmaz ve çerez yazmaz:
 * kullanıcı yeni parolasıyla olağan girişten geçer, ikinci faktör atlanmaz
 * (ADR-035).
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    await resetPassword(stringField(body, "token"), stringField(body, "password"), forwardedFor(request));
    return new Response(null, { status: 204 });
  });
}

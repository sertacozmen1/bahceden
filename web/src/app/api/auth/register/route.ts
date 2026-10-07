import { register } from "@/features/account/api";
import type { BffMessageResponse } from "@/features/account/bff";
import { forwardedFor, handleBffMutation, stringField } from "@/features/account/routeSupport";

/**
 * Kayıt (BFF). `api` her durumda aynı yanıtı döner (design.md D4); burada da
 * aynen iletilir. Kayıt oturum açmaz: kullanıcı ayrıca giriş yapar.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    const accepted = await register(stringField(body, "email"), stringField(body, "password"), forwardedFor(request));
    const response: BffMessageResponse = { message: accepted?.message ?? "" };
    return Response.json(response, { status: 202 });
  });
}

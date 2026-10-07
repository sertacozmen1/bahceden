import { login } from "@/features/account/api";
import type { BffLoginResponse } from "@/features/account/bff";
import { forwardedFor, handleBffMutation, stringField } from "@/features/account/routeSupport";
import { writeSession } from "@/lib/session";

/**
 * Giriş (BFF, design.md D6). Token'lar `HttpOnly` çerezlere yazılır ve yanıtta
 * yer almaz. İkinci faktör açık hesapta token yoktur; tarayıcıya
 * yalnızca ikinci adımın beklendiği söylenir.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    const result = await login(stringField(body, "email"), stringField(body, "password"), forwardedFor(request));
    if (result.kind === "challenge") {
      const response: BffLoginResponse = { outcome: "CHALLENGE_REQUIRED", message: result.challenge.message };
      return Response.json(response);
    }
    await writeSession(result.tokens);
    const response: BffLoginResponse = { outcome: "AUTHENTICATED" };
    return Response.json(response);
  });
}

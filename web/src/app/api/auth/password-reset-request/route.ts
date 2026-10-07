import { requestPasswordReset } from "@/features/account/api";
import type { BffMessageResponse } from "@/features/account/bff";
import { forwardedFor, handleBffMutation, stringField } from "@/features/account/routeSupport";

/**
 * Parola sıfırlama talebi (BFF). `api` her durumda aynı yanıtı döner
 * (ADR-034); burada da aynen iletilir.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    const accepted = await requestPasswordReset(stringField(body, "email"), forwardedFor(request));
    const response: BffMessageResponse = { message: accepted?.message ?? "" };
    return Response.json(response, { status: 202 });
  });
}

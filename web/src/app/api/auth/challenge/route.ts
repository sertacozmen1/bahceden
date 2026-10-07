import { completeChallenge } from "@/features/account/api";
import type { BffLoginResponse } from "@/features/account/bff";
import { forwardedFor, handleBffMutation, stringField } from "@/features/account/routeSupport";
import { writeSession } from "@/lib/session";

/** Girişin ikinci adımı (BFF, design.md D8): kod doğrulanınca çerezler yazılır. */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    const tokens = await completeChallenge(stringField(body, "email"), stringField(body, "code"), forwardedFor(request));
    await writeSession(tokens);
    const response: BffLoginResponse = { outcome: "AUTHENTICATED" };
    return Response.json(response);
  });
}

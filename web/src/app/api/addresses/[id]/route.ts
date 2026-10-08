import { handleBffMutation } from "@/features/account/routeSupport";
import { deleteAddress, updateAddress } from "@/features/addresses/api";
import { addressBodyFrom } from "@/features/addresses/bff";
import { readAccessToken } from "@/lib/session";

/**
 * Adres güncelleme ve silme (BFF, D5). Başkasının adresi `api`'de 404 olur;
 * geçersiz kimlik `api`'ye hiç gitmez.
 */
export async function PUT(request: Request, context: RouteContext<"/api/addresses/[id]">): Promise<Response> {
  const id = parseId((await context.params).id);
  if (id === undefined) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async (body) =>
    Response.json(await updateAddress(await readAccessToken(), id, addressBodyFrom(body))),
  );
}

export async function DELETE(request: Request, context: RouteContext<"/api/addresses/[id]">): Promise<Response> {
  const id = parseId((await context.params).id);
  if (id === undefined) {
    return new Response(null, { status: 404 });
  }
  return handleBffMutation(request, async () => {
    await deleteAddress(await readAccessToken(), id);
    return new Response(null, { status: 204 });
  });
}

function parseId(raw: string): number | undefined {
  const id = Number(raw);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

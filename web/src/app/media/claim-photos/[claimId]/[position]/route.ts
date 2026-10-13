import { claimPhotoSizes, downloadClaimPhoto } from "@/features/claims/api";
import { claimBffPaths } from "@/features/claims/bff";
import { serveDocument } from "@/features/seller/documentResponse";
import { readAccessToken } from "@/lib/session";

/**
 * Talep fotoğrafının tarayıcıya aktarımı (ADR-084). Yalnızca talebin
 * alıcısı, satıcısı ve admin görür; denetim `api`'dedir, token çerezden okunur.
 * `nosniff` ve önbelleksizlik başlıkları aynen geçer (belge aktarımıyla aynı
 * yardımcı, K9). Geçersiz kimlik veya boyut `api`'ye gitmez.
 */
export async function GET(
  request: Request,
  context: RouteContext<"/media/claim-photos/[claimId]/[position]">,
): Promise<Response> {
  const params = await context.params;
  const claimId = Number(params.claimId);
  const position = Number(params.position);
  const size = claimPhotoSizes.find((candidate) => candidate === new URL(request.url).searchParams.get("size"));
  if (!Number.isSafeInteger(claimId) || claimId <= 0 || !Number.isInteger(position) || position < 0 || size === undefined) {
    return new Response(null, { status: 404 });
  }
  const accessToken = await readAccessToken();
  return serveDocument(
    () => downloadClaimPhoto(accessToken, claimId, position, size),
    claimBffPaths.photo(claimId, position, size),
  );
}

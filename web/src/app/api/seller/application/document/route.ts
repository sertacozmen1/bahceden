import { downloadOwnSellerDocument } from "@/features/seller/api";
import { sellerBffPaths } from "@/features/seller/bff";
import { serveDocument } from "@/features/seller/documentResponse";
import { readAccessToken } from "@/lib/session";

/**
 * Oturum sahibinin güncel başvuru belgesi (BFF, add-seller-onboarding D6).
 * Belge yeni sekmede açılır; koruma başlıkları ve akış `serveDocument`'ta.
 */
export async function GET(): Promise<Response> {
  const accessToken = await readAccessToken();
  return serveDocument(() => downloadOwnSellerDocument(accessToken), sellerBffPaths.document);
}

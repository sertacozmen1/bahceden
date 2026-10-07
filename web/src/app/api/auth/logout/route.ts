import { logout } from "@/features/account/api";
import { handleBffMutation } from "@/features/account/routeSupport";
import { clearSession, readRefreshToken } from "@/lib/session";

/**
 * Çıkış (BFF). Önce `api` tarafında oturum zinciri iptal edilir, sonra çerezler
 * silinir. `api`'ye ulaşılamasa bile çerezler silinir: kullanıcı bu tarayıcıda
 * oturumsuz kalır, yenileme token'ı ise süresi dolunca kendiliğinden geçersizleşir.
 */
export async function POST(request: Request): Promise<Response> {
  return handleBffMutation(request, async () => {
    const refreshToken = await readRefreshToken();
    try {
      if (refreshToken !== undefined) {
        await logout(refreshToken);
      }
    } finally {
      await clearSession();
    }
    return new Response(null, { status: 204 });
  });
}

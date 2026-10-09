import type { NextRequest } from "next/server";
import { refreshSession } from "@/features/account/api";
import { ApiRequestError } from "@/lib/apiError";
import { forwardedFor } from "@/features/account/routeSupport";
import { isAllowedNavigation, safeReturnPath } from "@/lib/sameOrigin";
import { clearSession, readRefreshToken, writeSession } from "@/lib/session";

/**
 * Oturum yenileme yönlendirmesi (design.md D6).
 *
 * Server Component çerez yazamaz; bu yüzden korunan bir sayfa erişim token'ının
 * süresinin dolduğunu görünce kullanıcıyı buraya yönlendirir. Yenileme çerezi
 * yalnızca `/api/auth` altında gönderildiği için yenileme de burada yapılır.
 * Başarılıysa kullanıcı geldiği sayfaya döner; değilse oturum çerezleri silinir
 * ve giriş ekranına gider.
 *
 * `silent=1` sessiz yenilemedir (ADR-070): herhangi bir sayfa, kullanıcı bir şey
 * istemeden oturumu tazelemek için buraya gelir. Yenileme başarısız olursa
 * kullanıcı giriş ekranına itilmez; çerezler silinir ve aynı sayfaya oturumsuz
 * döner. `api`'ye ulaşılamazsa da aynı sayfaya döner, oturum silinmez.
 *
 * Yenileme tek bir gezinti içinde yapılır: aynı token'la eşzamanlı iki yenileme
 * (ki `api` bunu sızıntı sayıp oturum zincirini iptal eder) oluşmaz.
 */
const FALLBACK_PATH = "/account";

/**
 * Göreli `Location` ile yönlendirme. Mutlak adres istek URL'sinden kurulursa
 * konteynerde makine adına (`http://<konteyner>:3000`) gider; tarayıcı göreli
 * adresi kendi gördüğü kökene göre çözer.
 */
function redirectTo(path: string): Response {
  return new Response(null, { status: 307, headers: { Location: path } });
}

export async function GET(request: NextRequest): Promise<Response> {
  const returnPath = safeReturnPath(request.nextUrl.searchParams.get("next"), FALLBACK_PATH);
  const silent = request.nextUrl.searchParams.get("silent") === "1";
  const toLogin = `/login?next=${encodeURIComponent(returnPath)}`;
  const onFailure = silent ? returnPath : toLogin;

  if (!isAllowedNavigation(request)) {
    return redirectTo(toLogin);
  }

  const refreshToken = await readRefreshToken();
  if (refreshToken === undefined) {
    await clearSession();
    return redirectTo(onFailure);
  }

  try {
    await writeSession(await refreshSession(refreshToken, forwardedFor(request)));
    return redirectTo(returnPath);
  } catch (error) {
    if (error instanceof ApiRequestError) {
      // api token'ı reddetti (süresi dolmuş, iptal edilmiş, kullanılmış): yeniden giriş.
      await clearSession();
      return redirectTo(onFailure);
    }
    // api'ye ulaşılamadı: oturumu silmek kullanıcıyı haksız yere çıkarırdı.
    if (silent) {
      return redirectTo(returnPath);
    }
    throw error;
  }
}

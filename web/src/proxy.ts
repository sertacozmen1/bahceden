import { type NextRequest, NextResponse } from "next/server";
import { REQUEST_PATH_HEADER, requestPathOf } from "@/lib/requestPath";

/**
 * Korunan yollarda isteğin tam adresini (yol + sorgu) bir istek başlığına
 * kopyalar (ADR-102): oturum kapısı layout'ta çalışır ve sorgu dizesini
 * göremez; yenileme ya da girişten sonra kullanıcı aynı süzgece döner.
 *
 * Yalnızca bu başlığı yazar. Çerezlere, token'a ve yanıta dokunmaz; oturum
 * yenilemez (ara katmanda yenileme ADR-023 ve ADR-070 ile reddedildi).
 */
export function proxy(request: NextRequest): NextResponse {
  const headers = new Headers(request.headers);
  headers.set(REQUEST_PATH_HEADER, requestPathOf(request.nextUrl));
  return NextResponse.next({ request: { headers } });
}

/** Oturum kapısı kullanan yollar (`requireSignedIn` / `requireRole`). */
export const config = {
  matcher: ["/account/:path*", "/admin/:path*", "/seller/:path*", "/cart/:path*", "/checkout/:path*"],
};

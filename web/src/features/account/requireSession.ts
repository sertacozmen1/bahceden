import type { Route } from "next";
import { redirect } from "next/navigation";
import { currentSession, type SessionState } from "@/features/account/api";

type SignedIn = Extract<SessionState, { kind: "signed-in" }>;

/**
 * Korunan sayfaların kapısı (AGENTS.md §12.5 "yetkisiz" durumu, design.md D6).
 *
 * Oturum yoksa giriş sayfasına; erişim token'ının süresi dolduysa önce bir
 * kez yenilemeye yönlendirir, yenileme kullanıcıyı `selfPath`'e geri getirir.
 * Sayfanın `layout.tsx` dosyasından çağrılır: layout yükleniyor sınırının
 * dışında çalıştığı için yönlendirme gerçek bir HTTP 307 olur. Sayfa aynı
 * çağrıyla veriyi alır; oturum istek başına bir kez okunur.
 */
export async function requireSignedIn(selfPath: Route): Promise<SignedIn> {
  const session = await currentSession();
  const next = encodeURIComponent(selfPath);
  if (session.kind === "anonymous") {
    redirect(`/login?next=${next}`);
  }
  if (session.kind === "expired") {
    redirect(`/api/auth/refresh?next=${next}`);
  }
  return session;
}

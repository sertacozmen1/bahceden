import type { Route } from "next";
import { notFound, redirect } from "next/navigation";
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
 *
 * Yenileme ucu bir Route Handler'dır, sayfa değil. Uygulama içi bağlantıyla
 * yapılan gezinti bir Route Handler'a yönlendirilirse istemci yönlendiricisi
 * onu izleyemez ve boş sayfada kalır. Sunucu iki gezinti türünü ayırt edemez
 * (Next.js yönlendirici başlıklarını `headers()`'tan çıkarır); bu yüzden kapı
 * her zaman önce `/session/refresh` sayfasına yönlendirir, o sayfa yenileme
 * ucuna tam sayfa gezintisiyle geçer.
 */
export async function requireSignedIn(selfPath: Route): Promise<SignedIn> {
  const session = await currentSession();
  const next = encodeURIComponent(selfPath);
  if (session.kind === "anonymous") {
    redirect(`/login?next=${next}`);
  }
  if (session.kind === "expired") {
    redirect(`/session/refresh?next=${next}`);
  }
  return session;
}

type AccountRole = SignedIn["account"]["roles"][number];

/**
 * Rol isteyen sayfaların kapısı (add-seller-onboarding D7): önce oturum
 * kapısı, sonra rol. Rolü olmayan kullanıcı "bulunamadı" görür; yönetim
 * yüzeyinin varlığı açığa çıkmaz. Bu yalnızca deneyimdir: yetkilendirme
 * otoritesi `api`'dir (AGENTS.md §12.1), rolsüz istek orada 403 alır.
 */
export async function requireRole(selfPath: Route, role: AccountRole): Promise<SignedIn> {
  const session = await requireSignedIn(selfPath);
  if (!session.account.roles.includes(role)) {
    notFound();
  }
  return session;
}

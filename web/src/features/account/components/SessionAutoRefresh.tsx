"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { bffPaths } from "@/features/account/bff";
import { startSessionRefresh } from "@/features/account/components/SessionRefreshRedirect";

/** Son sessiz yenileme denemesinin zamanı; yalnızca döngü koruması, token değil. */
const LAST_ATTEMPT_KEY = "bahceden.silentRefreshAt";

/** Bu süre içinde ikinci bir sessiz yenileme denenmez (yenileme döngüsüne karşı). */
const RETRY_AFTER_MS = 60_000;

/** Kendi yönlendirmesini yapan ara sayfa; orada ikinci bir yenileme başlatılmaz. */
const REFRESH_PAGE_PATH = "/session/refresh";

/**
 * Sessiz oturum yenileme (ADR-070). Erişim token'ı dolmuş ama oturum çerezi
 * duran bir kullanıcı herhangi bir sayfaya geldiğinde (ör. tarayıcıyı kapatıp
 * açtığında) üst başlık bu bileşeni render eder; bileşen yenileme ucuna gider
 * ve kullanıcı aynı sayfaya oturumu açık olarak döner. Yenileme reddedilirse
 * uç kullanıcıyı giriş ekranına değil, yine aynı sayfaya oturumsuz döndürür.
 */
export function SessionAutoRefresh() {
  const pathname = usePathname();

  useEffect(() => {
    if (pathname === REFRESH_PAGE_PATH || attemptedRecently()) {
      return;
    }
    const current = `${window.location.pathname}${window.location.search}`;
    startSessionRefresh(`${bffPaths.refresh}?next=${encodeURIComponent(current)}&silent=1`, current);
  }, [pathname]);

  return null;
}

/**
 * Son deneme yakın zamandaysa `true` döner; değilse şimdiki zamanı kaydeder.
 * Depolama kapalıysa (gizli pencere, engelli site verisi) koruma olmadan devam
 * edilir: yenileme ucu başarısızlıkta oturum çerezlerini sildiği için döngü
 * kendiliğinden kesilir.
 */
function attemptedRecently(): boolean {
  try {
    const last = Number(window.sessionStorage.getItem(LAST_ATTEMPT_KEY));
    const now = Date.now();
    if (Number.isFinite(last) && now - last < RETRY_AFTER_MS) {
      return true;
    }
    window.sessionStorage.setItem(LAST_ATTEMPT_KEY, String(now));
    return false;
  } catch {
    return false;
  }
}

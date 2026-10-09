"use client";

import { useEffect } from "react";

/** Sekmeler arası yenileme kilidinin adı (ADR-070). */
const LOCK_NAME = "bahceden-session-refresh";

/**
 * Kilidi tutan sekme yenileme ucuna gidince sayfası kapanır ve kilit kendiliğinden
 * bırakılır. Gezinti bir sebeple sayfayı kapatmazsa kilit bu süre sonunda bırakılır;
 * bekleyen sekme sonsuza dek beklemez.
 */
const LOCK_HOLD_MS = 10_000;

/**
 * Yenileme ucuna tam sayfa gezintisi (ADR-023, ADR-070).
 *
 * Aynı anda birden çok sekme yenilemek isterse yalnızca biri yenileme ucuna gider
 * (Web Locks API). Bekleyen sekme, kilit bırakıldığında çerezler artık yenilendiği
 * için yenileme yapmadan `returnHref` adresine döner. Tarayıcı kilidi desteklemiyorsa
 * doğrudan gidilir; olası eşzamanlı yenilemeyi `api`'nin tolerans penceresi karşılar
 * (ADR-069).
 *
 * Geçmişe yeni kayıt eklenmez (`replace`): geri tuşu döngüye girmez.
 */
export function startSessionRefresh(refreshHref: string, returnHref: string): void {
  if (!("locks" in navigator)) {
    window.location.replace(refreshHref);
    return;
  }
  void navigator.locks.request(LOCK_NAME, { ifAvailable: true }, async (lock) => {
    if (lock === null) {
      await navigator.locks.request(LOCK_NAME, () => {
        window.location.replace(returnHref);
      });
      return;
    }
    window.location.replace(refreshHref);
    await new Promise((resolve) => setTimeout(resolve, LOCK_HOLD_MS));
  });
}

/**
 * Korunan sayfanın ara sayfasında kullanılır: uygulama içi gezinti bir Route
 * Handler'a yönlendirilemediği için tarayıcıyı yenileme ucuna bu bileşen götürür.
 */
export function SessionRefreshRedirect({
  href,
  returnHref,
}: {
  readonly href: string;
  readonly returnHref: string;
}) {
  useEffect(() => {
    startSessionRefresh(href, returnHref);
  }, [href, returnHref]);
  return null;
}

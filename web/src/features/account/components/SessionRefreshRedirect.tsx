"use client";

import { useEffect } from "react";

/**
 * Yenileme ucuna tam sayfa gezintisi (ADR-023). Uygulama içi gezinti bir
 * Route Handler'a yönlendirilemediği için bu bileşen tarayıcıyı adrese
 * kendisi götürür; geçmişe yeni kayıt eklemez (`replace`), geri tuşu döngüye
 * girmez.
 */
export function SessionRefreshRedirect({ href }: { readonly href: string }) {
  useEffect(() => {
    window.location.replace(href);
  }, [href]);
  return null;
}
